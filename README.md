# osky-sample

This repository contains sample ADS-B data taken from the OpenSky Network.
Raw messages are stored using the [Avro serialization system](https://avro.apache.org/) with the following Schema:

```
{
  "name": "ModeSEncodedMessage",
  "type": "record",
  "namespace": "org.opensky.avro.v2",
  "fields": [
      {"name": "sensorType",      		"type": "string"},
      {"name": "sensorLatitude",   		"type": ["double", "null"]},
      {"name": "sensorLongitude",   	"type": ["double", "null"]},
      {"name": "sensorAltitude",      	"type": ["double", "null"]},
      {"name": "timeAtServer",   		"type": "double"},
      {"name": "timeAtSensor",   		"type": ["double", "null"]},
      {"name": "timestamp",   			"type": ["double", "null"]},
      {"name": "rawMessage",     		"type": "string"},
      {"name": "sensorSerialNumber",    "type": "int"},
      {"name": "RSSIPacket",    		"type": ["double", "null"]},
      {"name": "RSSIPreamble",    		"type": ["double", "null"]},
      {"name": "SNR",    				"type": ["double", "null"]},
      {"name": "confidence",    		"type": ["double", "null"]}
  ]
}
```
If you 

## Decoding

Use the included decoder to examine the dataset:

`java -jar decoder.jar avro/raw20150421_sample.avro 100`

will print out the first 100 raw and decoded records. The [source code](https://github.com/openskynetwork/java-adsb/blob/master/src/main/java/org/opensky/example/OskySampleReader.java) for this decoder can be found in the [java-adsb](https://github.com/openskynetwork/java-adsb) repository.

### avro/raw20150421\_sample.avro

This file contains 303831 records as received by the OpenSky Network on April,
21st between 12:00 and 12:05 UTC.

