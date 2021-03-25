// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace search::query {

struct QueryVisitor;

/**
  This is the base of any node in the query tree. Both leaf nodes (terms)
  and operator nodes (AND, NOT, OR, PHRASE, NEAR, ONEAR, etc).
*/
class Node {
 public:
    typedef std::unique_ptr<Node> UP;

    virtual ~Node() = default;
    virtual void accept(QueryVisitor &visitor) = 0;
    virtual bool isIntermediate() const { return false; }
};

}

