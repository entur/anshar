# Profile-validation

## Concept
For correct XML parsing it is possible to perform a validation against the SIRI XML-Schema. This, however, only validates the 
XML structure, and the contents to a certain degree. To ensure that the usage of the standard is aligned
across operators, a SIRI profile has been defined that limits the options and describes how specific fields 
should be used. The profile validation then has full control over all aspects of the XML, and is able to validate both single
attributes, and combinations of fields.

When validation is enabled, it is for a single subscription only, and does not affect other data.

When switching on validation in Anshar, both schema-validation and profile-validation is performed, and is presented in a
combined report.

## Technical description
### Clean start
When validation is switched on for a subscription, all previous validation-data for that specific subscription is cleared
to ensure that only the most recent data is validated.

### Asynchronous
Validation is performed asynchronously to reduce impact on overall performance. When validation fails, it does not affect 
further processing of the data.

### Annotations
Profile validation is annotation-based (@Validator). Validation-rules are implemented by adding an annotation and specify 
profile-name, and which SIRI datatype the rule applies to. The validator then loads rulesets and loops through all applicable 
rules to provide a complete report. 

### Auto shut off
The validator stores all validation results and a zipped byte-array of the actual XML in a distributed
map. To avoid memory-issues, the validator is automatically switched off when either 20 XML-documents have been validated,
or the combined size of the zipped XML has reached 4 MB - both values are configurable. The results and all related data is
available until validation is switched on again.
