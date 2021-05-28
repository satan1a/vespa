// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_stripe_utils.h"
#include <vespa/vespalib/util/alloc.h>
#include <cassert>

namespace storage {

namespace {

constexpr uint8_t used_bits_of(uint64_t key) noexcept {
    return static_cast<uint8_t>(key & 0b11'1111ULL);
}

}

size_t
stripe_of_bucket_key(uint64_t key, uint8_t n_stripe_bits) noexcept
{
    if (n_stripe_bits == 0) {
        return 0;
    }
    assert(used_bits_of(key) >= n_stripe_bits);
    // Since bucket keys have count-bits at the LSB positions, we want to look at the MSBs instead.
    return (key >> (64 - n_stripe_bits));
}

uint8_t
calc_num_stripe_bits(size_t n_stripes) noexcept
{
    assert(n_stripes > 0);
    if (n_stripes == 1) {
        return 0;
    }
    assert(n_stripes <= MaxStripes);
    assert(n_stripes == vespalib::roundUp2inN(n_stripes));

    auto result = vespalib::Optimized::msbIdx(n_stripes);
    assert(result <= MaxStripeBits);
    return result;
}

}