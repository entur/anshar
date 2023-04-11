# Mapping

## Background
Some realtime-providers may have known quality-issues that require the data to be patched/fixed etc. before the data can be used as expected.

Anshar has support for defining a set of mapping-rules that will be applied to the data when it is received.

## Configuration
The mapping-adapter to be used for a subscription should be set in the parameter `mappingAdapterId` for that subscription. The value refers to the label defined for annotation @Mapping
E.g. `mappingAdapter: myMappingRules` will apply the rules defined in a class as:
``` 
@Mapping(id="myMappingRules")
public class MyValueAdapters extends MappingAdapter {

    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscription) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();

        valueAdapters.add(new Adapter1(StopPlaceRef.class));
        valueAdapters.add(new Adapter2(StopPointRefStructure.class));

        return valueAdapters;
    }
}
```
ValueAdapter-rules will be applied in the order they are defined. 

## Mapping adapters
There are two types of adapters currently supported: 
- "ValueAdapter"
  - Reflection-based rules that are applied to specific values based on the data type. 
  - Typical usage is e.g. to add missing prefixes, convert specific fields etc.
- "PostProcessor"
  - Extends ValueAdapter, but also implements interface `PostProcessor` 
  - Supports altering the entire SIRI-data object.
  - Typical usage is e.g. to verify/add missing fields.
