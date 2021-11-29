// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_shared_threading_service.h"
#include "shared_threading_service_config.h"
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/syncable.h>
#include <memory>

namespace proton {

/**
 * Class containing the thread executors that are shared across all document dbs.
 */
class SharedThreadingService : public ISharedThreadingService {
private:
    vespalib::ThreadStackExecutor _warmup;
    std::shared_ptr<vespalib::SyncableThreadExecutor> _shared;
    std::unique_ptr<vespalib::ISequencedTaskExecutor> _field_writer;

public:
    SharedThreadingService(const SharedThreadingServiceConfig& cfg);

    vespalib::SyncableThreadExecutor& warmup_raw() { return _warmup; }
    std::shared_ptr<vespalib::SyncableThreadExecutor> shared_raw() { return _shared; }

    vespalib::ThreadExecutor& warmup() override { return _warmup; }
    vespalib::ThreadExecutor& shared() override { return *_shared; }
    vespalib::ISequencedTaskExecutor* field_writer() override { return _field_writer.get(); }
};

}