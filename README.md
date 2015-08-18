# osky-sample

This repository contains a 5 minutes sample of ADS-B data as stored by the OpenSky Network. The raw messages are stored using the [Avro serialization system](https://avro.apache.org/) with the following schema:

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
A example decoder is also provided (see below). We've been storing all messages received by our network for more than two years in this format. If you need more data for your research, contribute to the network with a sensor or send a mail to contact@opensky-network.org.

## The data sample

The OpenSky avro sample can be found [here](avro/raw20150421_sample.avro). This file contains 304131 records as received by the OpenSky Network on April,
21st between 12:00 and 12:05 UTC. For more information, run e.g. `java -cp decoder.jar org.opensky.tools.AvroInfo avro/raw20150421_sample.avro` (see below).

## Decoding

Use the included decoder to examine the dataset:

`java -jar decoder.jar avro/raw20150421_sample.avro 100`

will print out the first 100 raw and decoded records. The [source code](https://github.com/openskynetwork/java-adsb/blob/master/src/main/java/org/opensky/example/OskySampleReader.java) for this decoder can be found in the [java-adsb](https://github.com/openskynetwork/java-adsb) repository.

## Additional tools

The archive decoder.jar is a complete build of the [java-adsb project](https://github.com/openskynetwork/java-adsb). It currently includes the following tools for processing OpenSky's avro files. 

#### AvroInfo

This tool parses the avro file and prints useful information. For more information, run `java -cp decoder.jar org.opensky.tools.AvroInfo` or try `java -cp decoder.jar org.opensky.tools.AvroInfo avro/raw20150421_sample.avro`.

#### Avro2Kml

This tool parses the avro, decodes the messages and outputs file in the Keyhole Markup Language (KML). This file can e.g. be displayed by Google Earth. It will include all selected tracks and additional information extracted from the avro file. An example screenshot of the result for the avro sample provided in this repository can be found [here](img/kml_example.png).

To generate a kml from the sample file, use `java -cp decoder.jar org.opensky.tools.Avro2Kml avro/raw20150421_sample.avro raw20150421_sample.kml`. Then open raw20150421_sample.kml using Google Earth. For more parameters (such as filters), run `java -cp decoder.jar org.opensky.tools.Avro2Kml -h`.

#### ExtractArea

This tool goes through OpenSky avro files and filters messages that were sent within a certain area. The area can be defined by a center coordinate and a radius. Output will be another avro filei.

Example: To filter all messages from a 10 km radius around Zurich airport, you can use `java -cp decoder.jar org.opensky.tools.ExtractArea -c 8.55,47.45 -r 10000 avro/raw20150421_sample.avro airport_zurich.avro`. Use Avro2Kml to see the result in Google Earth.

#### Avro2SQLite

This tool decodes the avro file and stores all positions and velocities in an sqlite database.
