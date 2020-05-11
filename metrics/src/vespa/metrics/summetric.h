// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class metrics::CounterMetric
 * @ingroup metrics
 *
 * @brief Counts a value that only moves upwards.
 *
 * NB! If you have a MetricSet subclass you want to create a sum for, use
 * MetricSet itself as the template argument. Otherwise you'll need to override
 * clone(...) in order to make it return the correct type for your
 * implementation.
 */

#pragma once

#include "metric.h"

namespace metrics {

class MetricSet;

template<typename AddendMetric>
class SumMetric : public Metric
{
public:
    class StartValue
    {
    private:
        std::vector<Metric::UP>     _startValueChildren;
        Metric::UP _startValue;

    public:
        typedef std::shared_ptr<StartValue> SP;
        StartValue(const AddendMetric &metric)
            : _startValueChildren(),
              _startValue(metric.clone(_startValueChildren, CLONE, 0, false)) {}
        const AddendMetric &getStartValue() const { return static_cast<const AddendMetric &>(*_startValue); }
    };

private:
    typename StartValue::SP          _startValue;
    std::vector<const AddendMetric*> _metricsToSum;

public:
    SumMetric(const String& name, Tags tags, const String& description, MetricSet* owner = 0);
    SumMetric(const SumMetric<AddendMetric>& other, std::vector<Metric::UP> & ownerList, MetricSet* owner = 0);
    ~SumMetric();

    Metric* clone( std::vector<Metric::UP> &, CopyType, MetricSet* owner, bool includeUnused = false) const override;

    /**
     * If you want to support sums of collections of metrics that may
     * be empty, you must supply a start value for the sum operation
     * by calling this function.
     **/
    void setStartValue(const AddendMetric &metric) { _startValue.reset(new StartValue(metric)); }
    typename StartValue::SP getStartValue() const { return _startValue; }

    void addMetricToSum(const AddendMetric&);
    void removeMetricFromSum(const AddendMetric&);

    void print(std::ostream&, bool verbose, const std::string& indent, uint64_t secondsPassed) const override;
    int64_t getLongValue(stringref id) const override;
    double getDoubleValue(stringref id) const override;
    void reset() override {}
    bool visit(MetricVisitor& visitor, bool tagAsAutoGenerated = false) const override;
    bool used() const override;
    void addMemoryUsage(MemoryConsumption&) const override;
    void printDebug(std::ostream&, const std::string& indent="") const override;
    void addToPart(Metric&) const override;
    void addToSnapshot(Metric&, std::vector<Metric::UP> &) const override;

private:
    friend struct MetricManagerTest;
    std::pair<std::vector<Metric::UP>, Metric::UP> generateSum() const;

    virtual void addTo(Metric&, std::vector<Metric::UP> *ownerList) const;
    bool isAddendType(const Metric* m) const;
};

} // metrics

