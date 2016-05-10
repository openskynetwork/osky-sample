# osky-sample

This repository contains a 5 minutes sample of ADS-B data as stored by the OpenSky Network and some tools to process them. The raw messages are stored using the [Avro serialization system](https://avro.apache.org/) with the following schema:

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

Please also check out the [slides](oskyws15_data_tools.pdf) on the data and tools of this repository.

## The data sample

The OpenSky avro sample can be found [here](avro/raw20150421_sample.avro). This file contains 304131 records as received by the OpenSky Network on April,
21st between 12:00 and 12:05 UTC. For more information, run e.g. `java -cp tools-1.0-fat.jar org.opensky.tools.AvroInfo avro/raw20150421_sample.avro` (see below).

## Example Tools

All tools are based on the [java-adsb project](https://github.com/openskynetwork/java-adsb). In order to use the tools, build the package using `mvn package`. The archive tools-1.0-fat.jar (target/) currently includes the following tools for processing OpenSky's avro files. 

#### AvroInfo

This tool parses the avro file and prints useful information. For more information, run `java -cp tools-1.0-fat.jar org.opensky.tools.AvroInfo` or try `java -cp tools-1.0-fat.jar org.opensky.tools.AvroInfo avro/raw20150421_sample.avro`.

#### Avro2Kml

This tool parses the avro, decodes the messages and outputs file in the Keyhole Markup Language (KML). This file can e.g. be displayed by Google Earth. It will include all selected tracks and additional information extracted from the avro file. An example screenshot of the result for the avro sample provided in this repository can be found [here](img/kml_example.png).

To generate a kml from the sample file, use `java -cp tools-1.0-fat.jar org.opensky.tools.Avro2Kml avro/raw20150421_sample.avro raw20150421_sample.kml`. Then open raw20150421_sample.kml using Google Earth. For more parameters (such as filters), run `java -cp tools-1.0-fat.jar org.opensky.tools.Avro2Kml -h`.

#### ExtractArea

This tool goes through OpenSky avro files and filters messages that were sent within a certain area. The area can be defined by a center coordinate and a radius. Output will be another avro filei.

Example: To filter all messages from a 10 km radius around Zurich airport, you can use `java -cp tools-1.0-fat.jar org.opensky.tools.ExtractArea -c 8.55,47.45 -r 10000 avro/raw20150421_sample.avro airport_zurich.avro`. Use Avro2Kml to see the result in Google Earth.

#### Avro2SQLite

This tool decodes the avro file and stores all positions and velocities in an sqlite database. Do a `SELECT sql FROM sqlite_master;` on a SQLite3 file created with this tool to see the database structure.

Example:
```bash
# convert sample avro to sqlite3 database
java -cp tools-1.0-fat.jar org.opensky.tools.Avro2SQLite avro/raw20150421_sample.avro raw20150421_sample.sqlite3
# ...

# check out database
sqlite3 raw20150421_sample.sqlite3 
# Example query: show me two random flights
# 
# sqlite> SELECT id, icao24, callsign, DATETIME(first, 'unixepoch'), DATETIME(last, 'unixepoch') FROM flights ORDER BY RANDOM() LIMIT 2;
# 370|406091|BAW605  |2015-04-21 12:00:01|2015-04-21 12:05:00
# 568|400f00|EZY78WC |2015-04-21 12:00:06|2015-04-21 12:01:24
# 
# Now show me the last position of the flight with the callsign EZY78WC (id 568):
# 
# sqlite> SELECT DATETIME(timestamp, 'unixepoch'), longitude, latitude, altitude FROM positions WHERE flight=568 ORDER BY timestamp DESC LIMIT 1;
# 2015-04-21 12:01:23|10.8334121704102|46.2030494819253|10058.4
```
#### AvroSort

This tool sort unsorted OpenSky avro files by the time the messages arrived at the OpenSky server (timeAtServer). This is important for a proper position decoding since the decoder assumes messages to be ordered in time. Simply run `java -cp tools-1.0-fat.jar org.opensky.tools.AvroSort sample.avro sample_sorted.avro`.

Note: the tools first loads all data into memory. So make sure you have enough memory available. Otherwise use AvroSplit to split the Avro file in consitent small files.

#### AvroSplit

This tool can be used to split one Avro file into an arbitrary number of smaller files without losing flight consistency. It can also be used to join multiple Avro files since it allows a arbitrary number of input as well as output files!

Usage:
  * `java -cp tools-1.0-fat.jar org.opensky.tools.AvroSplit -o outfile -n 5 sample.avro` -- splits sample.avro into 5 files called outfile1.avro, outfile2.avro, ...
  * `java -cp tools-1.0-fat.jar org.opensky.tools.AvroSplit -o outfile in1.avro in2.avro ...` -- joins input files to one avro file outfile1.avro
