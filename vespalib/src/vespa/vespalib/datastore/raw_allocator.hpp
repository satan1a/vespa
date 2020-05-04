// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_allocator.h"
#include "bufferstate.h"

namespace vespalib::datastore {

template <typename EntryT, typename RefT>
RawAllocator<EntryT, RefT>::RawAllocator(DataStoreBase &store, uint32_t typeId)
    : _store(store),
      _typeId(typeId)
{
}

template <typename EntryT, typename RefT>
typename RawAllocator<EntryT, RefT>::HandleType
RawAllocator<EntryT, RefT>::alloc(size_t numElems, size_t extraElems)
{
    _store.ensureBufferCapacity(_typeId, numElems + extraElems);
    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    BufferState &state = _store.getBufferState(activeBufferId);
    assert(state.isActive());
    size_t oldBufferSize = state.size();
    if (RefT::isAlignedType) {
        // AlignedEntryRef constructor scales down offset by alignment
        RefT ref(oldBufferSize, activeBufferId);
        EntryT *buffer = _store.getEntry<EntryT>(ref);
        state.pushed_back(numElems);
        return HandleType(ref, buffer);
    } else {
        // Must perform scaling ourselves, according to array size
        size_t arraySize = state.getArraySize();
        assert((numElems % arraySize) == 0u);
        RefT ref((oldBufferSize / arraySize), activeBufferId);
        EntryT *buffer = _store.getEntryArray<EntryT>(ref, arraySize);
        state.pushed_back(numElems);
        return HandleType(ref, buffer);
    }
}

}

