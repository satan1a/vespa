// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package auth0

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"sort"
	"sync"
	"time"

	"github.com/joeshaw/envdecode"
	"github.com/lestrrat-go/jwx/jwt"
	"github.com/pkg/browser"
	"github.com/vespa-engine/vespa/client/go/auth"
	"github.com/vespa-engine/vespa/client/go/util"
)

const accessTokenExpThreshold = 5 * time.Minute

var errUnauthenticated = errors.New("not logged in. Try 'vespa login'")

type config struct {
	Systems map[string]System `json:"systems"`
}

type System struct {
	AccessToken string    `json:"access_token,omitempty"`
	Scopes      []string  `json:"scopes,omitempty"`
	ExpiresAt   time.Time `json:"expires_at"`
}

type Auth0 struct {
	Authenticator *auth.Authenticator
	system        string
	initOnce      sync.Once
	errOnce       error
	Path          string
	config        config
}

// default to vespa-cd.auth0.com
var (
	authCfg struct {
		Audience           string `env:"AUTH0_AUDIENCE,default=https://vespa-cd.auth0.com/api/v2/"`
		ClientID           string `env:"AUTH0_CLIENT_ID,default=4wYWA496zBP28SLiz0PuvCt8ltL11DZX"`
		DeviceCodeEndpoint string `env:"AUTH0_DEVICE_CODE_ENDPOINT,default=https://vespa-cd.auth0.com/oauth/device/code"`
		OauthTokenEndpoint string `env:"AUTH0_OAUTH_TOKEN_ENDPOINT,default=https://vespa-cd.auth0.com/oauth/token"`
	}
)

func ContextWithCancel() context.Context {
	ctx, cancel := context.WithCancel(context.Background())
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt)
	go func() {
		<-ch
		defer cancel()
		os.Exit(0)
	}()
	return ctx
}

// GetAuth0 will try to initialize the config context, as well as figure out if
// there's a readily available system.
func GetAuth0(configPath string, systemName string) (*Auth0, error) {
	a := Auth0{}
	a.Path = configPath
	a.system = systemName
	if err := envdecode.StrictDecode(&authCfg); err != nil {
		return nil, fmt.Errorf("could not decode env: %w", err)
	}
	a.Authenticator = &auth.Authenticator{
		Audience:           authCfg.Audience,
		ClientID:           authCfg.ClientID,
		DeviceCodeEndpoint: authCfg.DeviceCodeEndpoint,
		OauthTokenEndpoint: authCfg.OauthTokenEndpoint,
	}
	return &a, nil
}

// IsLoggedIn encodes the domain logic for determining whether we're
// logged in. This might check our config storage, or just in memory.
func (a *Auth0) IsLoggedIn() bool {
	// No need to check errors for initializing context.
	_ = a.init()

	if a.system == "" {
		return false
	}

	// Parse the access token for the system.
	token, err := jwt.ParseString(a.config.Systems[a.system].AccessToken)
	if err != nil {
		return false
	}

	// Check if token is valid.
	if err = jwt.Validate(token, jwt.WithIssuer("https://vespa-cd.auth0.com/")); err != nil {
		return false
	}

	return true
}

// PrepareSystem loads the System, refreshing its token if necessary.
// The System access token needs a refresh if:
// 1. the System scopes are different from the currently required scopes - (auth0 changes).
// 2. the access token is expired.
func (a *Auth0) PrepareSystem(ctx context.Context) (System, error) {
	if err := a.init(); err != nil {
		return System{}, err
	}
	s, err := a.getSystem()
	if err != nil {
		return System{}, err
	}

	if s.AccessToken == "" || scopesChanged(s) {
		s, err = RunLogin(ctx, a, true)
		if err != nil {
			return System{}, err
		}
	} else if isExpired(s.ExpiresAt, accessTokenExpThreshold) {
		// check if the stored access token is expired:
		// use the refresh token to get a new access token:
		tr := &auth.TokenRetriever{
			Authenticator: a.Authenticator,
			Secrets:       &auth.Keyring{},
			Client:        http.DefaultClient,
		}

		res, err := tr.Refresh(ctx, a.system)
		if err != nil {
			// ask and guide the user through the login process:
			fmt.Println(fmt.Errorf("failed to renew access token, %s", err))
			s, err = RunLogin(ctx, a, true)
			if err != nil {
				return System{}, err
			}
		} else {
			// persist the updated system with renewed access token
			s.AccessToken = res.AccessToken
			s.ExpiresAt = time.Now().Add(
				time.Duration(res.ExpiresIn) * time.Second,
			)

			err = a.AddSystem(s)
			if err != nil {
				return System{}, err
			}
		}
	}

	return s, nil
}

// isExpired is true if now() + a threshold is after the given date
func isExpired(t time.Time, threshold time.Duration) bool {
	return time.Now().Add(threshold).After(t)
}

// scopesChanged compare the System scopes
// with the currently required scopes.
func scopesChanged(s System) bool {
	want := auth.RequiredScopes()
	got := s.Scopes

	sort.Strings(want)
	sort.Strings(got)

	if (want == nil) != (got == nil) {
		return true
	}

	if len(want) != len(got) {
		return true
	}

	for i := range s.Scopes {
		if want[i] != got[i] {
			return true
		}
	}

	return false
}

func (a *Auth0) getSystem() (System, error) {
	if err := a.init(); err != nil {
		return System{}, err
	}

	s, ok := a.config.Systems[a.system]
	if !ok {
		return System{}, fmt.Errorf("unable to find system: %s; run 'vespa login' to configure a new system", a.system)
	}

	return s, nil
}

// AddSystem assigns an existing, or new System. This is expected to be called
// after a login has completed.
func (a *Auth0) AddSystem(s System) error {
	_ = a.init()

	// If we're dealing with an empty file, we'll need to initialize this map.
	if a.config.Systems == nil {
		a.config.Systems = map[string]System{}
	}

	a.config.Systems[a.system] = s

	if err := a.persistConfig(); err != nil {
		return fmt.Errorf("unexpected error persisting config: %w", err)
	}

	return nil
}

func (a *Auth0) persistConfig() error {
	dir := filepath.Dir(a.Path)
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		if err := os.MkdirAll(dir, 0700); err != nil {
			return err
		}
	}

	buf, err := json.MarshalIndent(a.config, "", "    ")
	if err != nil {
		return err
	}

	if err := ioutil.WriteFile(a.Path, buf, 0600); err != nil {
		return err
	}

	return nil
}

func (a *Auth0) init() error {
	a.initOnce.Do(func() {
		if a.errOnce = a.initContext(); a.errOnce != nil {
			return
		}
	})
	return a.errOnce
}

func (a *Auth0) initContext() (err error) {
	if _, err := os.Stat(a.Path); os.IsNotExist(err) {
		return errUnauthenticated
	}

	var buf []byte
	if buf, err = ioutil.ReadFile(a.Path); err != nil {
		return err
	}

	if err := json.Unmarshal(buf, &a.config); err != nil {
		return err
	}

	return nil
}

// RunLogin runs the login flow guiding the user through the process
// by showing the login instructions, opening the browser.
// Use `expired` to run the login from other commands setup:
// this will only affect the messages.
func RunLogin(ctx context.Context, a *Auth0, expired bool) (System, error) {
	if expired {
		fmt.Println("Please sign in to re-authorize the CLI.")
	}

	state, err := a.Authenticator.Start(ctx)
	if err != nil {
		return System{}, fmt.Errorf("could not start the authentication process: %w", err)
	}

	fmt.Printf("Your Device Confirmation code is: %s\n\n", state.UserCode)
	fmt.Println("Press Enter to open the browser to log in or ^C to quit...")
	fmt.Scanln()

	err = browser.OpenURL(state.VerificationURI)

	if err != nil {
		fmt.Printf("Couldn't open the URL, please do it manually: %s.", state.VerificationURI)
	}

	var res auth.Result
	err = util.Spinner("Waiting for login to complete in browser", func() error {
		res, err = a.Authenticator.Wait(ctx, state)
		return err
	})

	if err != nil {
		return System{}, fmt.Errorf("login error: %w", err)
	}

	fmt.Print("\n")
	fmt.Println("Successfully logged in.")
	fmt.Print("\n")

	// store the refresh token
	secretsStore := &auth.Keyring{}
	err = secretsStore.Set(auth.SecretsNamespace, a.system, res.RefreshToken)
	if err != nil {
		// log the error but move on
		fmt.Println("Could not store the refresh token locally, please expect to login again once your access token expired.")
	}

	s := System{
		AccessToken: res.AccessToken,
		ExpiresAt:   time.Now().Add(time.Duration(res.ExpiresIn) * time.Second),
		Scopes:      auth.RequiredScopes(),
	}
	err = a.AddSystem(s)
	if err != nil {
		return System{}, fmt.Errorf("could not add system to config: %w", err)
	}

	return s, nil
}