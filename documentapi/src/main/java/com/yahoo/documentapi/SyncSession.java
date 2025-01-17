// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.time.Duration;

import static com.yahoo.documentapi.DocumentOperationParameters.parameters;

/**
 * A session for synchronous access to a document repository. This class
 * provides simple document access where throughput is not a concern.
 *
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
public interface SyncSession extends Session {

    /**
     * Puts a document. When this method returns, the document is safely
     * received. This enables setting condition compared to using Document.
     *
     * @param documentPut the DocumentPut operation
     */
    void put(DocumentPut documentPut);

    /**
     * Puts a document. When this method returns, the document is safely received.
     *
     * @param documentPut the DocumentPut operation
     * @param priority the priority with which to perform this operation
     * @deprecated specifying explicit operation priority is deprecated
     */
    @Deprecated(forRemoval = true) // TODO: Remove on Vespa 8
    @SuppressWarnings("removal") // TODO: Remove on Vespa 8
    default void put(DocumentPut documentPut, DocumentProtocol.Priority priority) {
        put(documentPut, parameters().withPriority(priority));
    }

    /**
     * Puts a document. When this method returns, the document is safely received.
     *
     * @param documentPut the DocumentPut operation
     * @param parameters parameters for the operation
     */
    default void put(DocumentPut documentPut, DocumentOperationParameters parameters) {
        put(documentPut);
    }

    /**
     * Gets a document.
     *
     * @param id the id of the document to get.
     * @return the known document having this id, or null if there is no document having this id
     * @throws UnsupportedOperationException thrown if this access does not support retrieving
     */
    default Document get(DocumentId id) { return get(id, null); }

    /**
     * Gets a document with an unspecified timeout
     *
     * @param id       the id of the document to get
     * @param fieldSet a comma-separated list of fields to retrieve
     * @param priority the priority with which to perform this operation
     * @return the document with this id, or null if there is none
     * @throws UnsupportedOperationException thrown if this does not support retrieving
     * @deprecated specifying explicit operation priority is deprecated. Set fieldSet via
     *             {@link #get(DocumentId, DocumentOperationParameters, Duration)} instead.
     */
    @Deprecated(forRemoval = true) // TODO: Remove on Vespa 8
    default Document get(DocumentId id, String fieldSet, DocumentProtocol.Priority priority) {
        return get(id, fieldSet, priority, null);
    }

    /**
     * Gets a document with timeout.
     *
     * @param id the id of the document to get
     * @param timeout timeout. If timeout is null, an unspecified default will be used
     * @return the document with this id, or null if there is none
     * @throws UnsupportedOperationException thrown if this access does not support retrieving
     * @throws DocumentAccessException on any messagebus error, including timeout ({@link com.yahoo.messagebus.ErrorCode#TIMEOUT}).
     */
    Document get(DocumentId id, Duration timeout);

    /**
     * Gets a document with timeout.
     *
     * @param id       the id of the document to get
     * @param fieldSet a comma-separated list of fields to retrieve
     * @param priority the priority with which to perform this operation
     * @param timeout timeout. If timeout is null, an unspecified default will be used
     * @return the known document having this id, or null if there is no document having this id
     * @throws UnsupportedOperationException thrown if this access does not support retrieving
     * @throws DocumentAccessException on any messagebus error, including timeout ({@link com.yahoo.messagebus.ErrorCode#TIMEOUT})
     * @deprecated specifying explicit operation priority is deprecated
     */
    @Deprecated(forRemoval = true) // TODO: Remove on Vespa 8
    Document get(DocumentId id, String fieldSet, DocumentProtocol.Priority priority, Duration timeout);

    /**
     * Gets a document with timeout.
     *
     * @param id       the id of the document to get
     * @param parameters parameters for the operation
     * @param timeout timeout. If timeout is null, an unspecified default will be used
     * @return the known document having this id, or null if there is no document having this id
     * @throws UnsupportedOperationException thrown if this access does not support retrieving
     * @throws DocumentAccessException on any messagebus error, including timeout ({@link com.yahoo.messagebus.ErrorCode#TIMEOUT})
     */
    default Document get(DocumentId id, DocumentOperationParameters parameters, Duration timeout) {
        return get(id, timeout);
    }

    /**
     * Removes a document if it is present and condition is fulfilled.
     *
     * @param documentRemove document to delete
     * @return true if the document with this id was removed, false otherwise
     */
    boolean remove(DocumentRemove documentRemove);

    /**
     * Removes a document if it is present.
     *
     * @param documentRemove document remove operation
     * @param priority the priority with which to perform this operation
     * @return true if the document with this id was removed, false otherwise.
     * @throws UnsupportedOperationException thrown if this access does not support removal
     * @deprecated specifying explicit operation priority is deprecated
     */
    @Deprecated(forRemoval = true) // TODO: Remove on Vespa 8
    boolean remove(DocumentRemove documentRemove, DocumentProtocol.Priority priority);

    /**
     * Removes a document if it is present.
     *
     * @param documentRemove document remove operation
     * @param parameters parameters for the operation
     * @return true if the document with this id was removed, false otherwise.
     * @throws UnsupportedOperationException thrown if this access does not support removal
     */
    default boolean remove(DocumentRemove documentRemove, DocumentOperationParameters parameters) {
        return remove(documentRemove);
    }

    /**
     * Updates a document.
     *
     * @param update the updates to perform
     * @return false if the updates could not be applied as the document does not exist and
     * {@link DocumentUpdate#setCreateIfNonExistent(boolean) create-if-non-existent} is not set.
     * @throws DocumentAccessException on update error, including but not limited to: 1. timeouts,
     * 2. the document exists but the {@link DocumentUpdate#setCondition(TestAndSetCondition) condition}
     * is not met.
     */
    boolean update(DocumentUpdate update);

    /**
     * Updates a document.
     *
     * @param update   the updates to perform.
     * @param priority the priority with which to perform this operation
     * @return false if the updates could not be applied as the document does not exist and
     * {@link DocumentUpdate#setCreateIfNonExistent(boolean) create-if-non-existent} is not set.
     * @throws DocumentAccessException on update error, including but not limited to: 1. timeouts,
     * 2. the document exists but the {@link DocumentUpdate#setCondition(TestAndSetCondition) condition}
     * is not met.
     * @deprecated specifying explicit operation priority is deprecated
     */
    @Deprecated(forRemoval = true) // TODO: Remove on Vespa 8
    boolean update(DocumentUpdate update, DocumentProtocol.Priority priority);

    /**
     * Updates a document.
     *
     * @param update   the updates to perform.
     * @param parameters parameters for the operation
     * @return false if the updates could not be applied as the document does not exist and
     * {@link DocumentUpdate#setCreateIfNonExistent(boolean) create-if-non-existent} is not set.
     * @throws DocumentAccessException on update error, including but not limited to: 1. timeouts,
     * 2. the document exists but the {@link DocumentUpdate#setCondition(TestAndSetCondition) condition}
     * is not met.
     */
    default boolean update(DocumentUpdate update, DocumentOperationParameters parameters) {
        return update(update);
    }

}
