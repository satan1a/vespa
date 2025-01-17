// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import java.util.function.Supplier;

/**
 * @author baldersheim
 */
@Deprecated(forRemoval = true, since = "7")
public abstract class ResourceFactory<T> {

    public abstract T create();

    public final Supplier<T> asSupplier() {
        return () -> create();
    }

}
