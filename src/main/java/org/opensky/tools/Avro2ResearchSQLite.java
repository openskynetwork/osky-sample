package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensky.avro.v2.ModeSEncodedMessage;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.Position;
import org.opensky.libadsb.PositionDecoder;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.VelocityOverGroundMsg;
import org.opensky.libadsb.msgs.ModeSReply.subtype;

/**
 * OpenSky AVRO to SQLite converter
 * Note: We assume, that messages are more or less ordered by time
 * 
 * Generates SQlite DB with positions and timestamps
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 *
 */
public class Avro2ResearchSQLite {
	Connection conn = null;
	Statement stmt = null;

	// initialize SQLite database
	public Avro2ResearchSQLite (String path) {
		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:"+path);
			conn.setAutoCommit(false);
			stmt = conn.createStatement();

			// create tables
			String sql = "CREATE TABLE sensor\n"+
					"(id INT PRIMARY KEY NOT NULL,\n"+
					" latitude REAL NOT NULL, -- sensor latitude\n"+
					" longitude REAL NOT NULL, -- sensor longitude\n"+
					" altitude REAL NOT NULL -- sensor altitude\n"+
					")";

			stmt.executeUpdate(sql);

			sql = "CREATE TABLE position\n"+
					"(sensor INT NOT NULL, -- references sensor from sensors table\n"+
					" timeAtServer REAL NOT NULL, -- unix timestamp\n"+
					" timeAtSensor INT NOT NULL, -- unix timestamp\n"+
					" timestamp INT NOT NULL, -- rolling timestamp\n"+
					" latitude REAL NOT NULL, -- in decimal degrees\n"+
					" longitude REAL NOT NULL, -- in decimal degrees\n"+
					" altitude REAL NOT NULL, -- in meters\n"+
					" rawMessage TEXT NOT NULL, -- raw message hex string\n"+
					" FOREIGN KEY(sensor) REFERENCES sensor(id)\n"+
					")";

			stmt.executeUpdate(sql);

			sql = "CREATE TABLE velocity\n"+
					"(sensor INT NOT NULL, -- references sensor from sensors table\n"+
					" timeAtServer REAL NOT NULL, -- unix timestamp\n"+
					" timeAtSensor INT NOT NULL, -- unix timestamp\n"+
					" timestamp INT NOT NULL, -- rolling timestamp\n"+
					" rawMessage TEXT NOT NULL, -- raw message hex string\n"+
					" horizontalSpeed REAL, -- in m/s\n"+
					" verticalSpeed REAL, -- in m/s\n"+
					" heading REAL, -- in clock-wise degrees from north\n"+
					" geoMinusBaro REAL, -- in meters\n"+
					" FOREIGN KEY(sensor) REFERENCES sensor(id)\n"+
					")";

			stmt.executeUpdate(sql);
			
		} catch ( Exception e ) {
			System.err.println("Could not open database: " + e.getMessage() );
			System.exit(1);
		}
	}

	public void insertSensor (int serial, Position pos) {
		try {
			String sql = String.format(Locale.ENGLISH, "INSERT OR REPLACE INTO sensor VALUES (%d, %f, %f, %f)",
					serial, pos.getLatitude(), pos.getLongitude(), pos.getAltitude());
			stmt.executeUpdate(sql);
		} catch (Exception e) {
			System.err.println("Could not create sensor: "+e.getMessage());
			System.exit(1);
		}
	}

	public void insertPosition (int sensor, double timeAtServer, long timeAtSensor, long timestamp, Position pos, String raw) {
		try {
			String sql = String.format(Locale.ENGLISH, "INSERT INTO position (sensor, timeAtServer, timeAtSensor, timestamp, latitude, longitude, altitude, rawMessage) VALUES (%d, %f, %d, %d, %f, %f, %f, \"%s\")",
					sensor, timeAtServer, timeAtSensor, timestamp, pos.getLatitude(), pos.getLongitude(), pos.getAltitude(), raw);
			stmt.executeUpdate(sql);
		} catch (Exception e) {
			System.err.println("Could not insert position: "+e.getMessage());
			e.printStackTrace();
		}
	}

	public void insertVelocity (int sensor, double timeAtServer, long timeAtSensor,
			long timestamp, Double horizSpeed, Double vertSpeed,
			Double heading, Double geoMinusBaro, String raw) {
		try {
			String sql = String.format(Locale.ENGLISH, "INSERT INTO velocity (sensor, timeAtServer, timeAtSensor, timestamp, rawMessage, horizontalSpeed, verticalSpeed, heading, geoMinusBaro) VALUES (%d, %f, %d, %d, \"%s\", %f, %f, %f, %f)",
					sensor, timeAtServer, timeAtSensor, timestamp, raw, horizSpeed, vertSpeed, heading, geoMinusBaro);
			stmt.executeUpdate(sql);
		} catch (Exception e) {
			System.err.println("Could not insert velocity: "+e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"Avro2TestData [options/filters] avro-file sqlite-file",
				"\nOpenSky AVRO to Positions SQLite converter\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}

	/**
	 * This class is a container for all information
	 * about flights that are relevant for the SQLite DB
	 * generation
	 */
	private class Flight {
		public Double last; // last position message received
		public PositionDecoder dec; // stateful position decoder

		public Flight (long id) {
			dec = new PositionDecoder();
		}
	}

	public static void main(String[] args) {

		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		opts.addOption("i", "icao24", true, "filter by icao 24-bit address (hex)");
		opts.addOption("s", "start", true, "only messages received after this time (unix timestamp)");
		opts.addOption("e", "end", true, "only messages received before this time (unix timestamp)");
		opts.addOption("n", "max-num", true, "max number of flights written to the SQLite DB");

		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		String filter_icao24 = null;
		Long filter_max = null;
		Double filter_start = null, filter_end = null;
		String inpath = null, outpath = null;
		try {
			cmd = parser.parse(opts, args);

			// parse arguments
			try {
				if (cmd.hasOption("i")) filter_icao24 = cmd.getOptionValue("i");
				if (cmd.hasOption("s")) filter_start = Double.parseDouble(cmd.getOptionValue("s"));
				if (cmd.hasOption("e")) filter_end = Double.parseDouble(cmd.getOptionValue("e"));
				if (cmd.hasOption("n")) filter_max = Long.parseLong(cmd.getOptionValue("n"));
			} catch (NumberFormatException e) {
				throw new ParseException("Invalid arguments: "+e.getMessage());
			}

			// print help
			if (cmd.hasOption("h")) {
				printHelp(opts);
				System.exit(0);
			}

			// get filename
			if (cmd.getArgList().size() != 2)
				throw new ParseException("Output SQLite file is missing!");
			inpath = cmd.getArgList().get(0);
			outpath = cmd.getArgList().get(1);

		} catch (ParseException e) {
			// parsing failed
			System.err.println(e.getMessage()+"\n");
			printHelp(opts);
			System.exit(1);
		}

		// check if file exists
		File avro = null;
		try {
			// check if output DB exists
			avro = new File(outpath);
			if (avro.exists() && !avro.isDirectory())
				throw new IOException("Output database already exists.");

			// check input file
			avro = new File(inpath);
			if(!avro.exists() || avro.isDirectory() || !avro.canRead())
				throw new FileNotFoundException("Avro file not found or cannot be read.");
		} catch (IOException e) {
			// avro file not found
			System.err.println("Error: "+e.getMessage()+"\n");
			System.exit(1);
		}

		// AVRO file reader
		DatumReader<ModeSEncodedMessage> datumReader =
				new SpecificDatumReader<ModeSEncodedMessage>(ModeSEncodedMessage.class);

		// some counters for statistics
		long msgs_cnt = 0, good_pos_cnt = 0, bad_pos_cnt = 0,
				flights_cnt = 0, filtered_cnt = 0, ignored_cnt = 0,
				last_msgs_cnt = 0;
		long last_time;

		// just a temporary instance for creating Flight-objects
		Avro2ResearchSQLite a2sql = new Avro2ResearchSQLite(outpath);
		try {
			// open input file
			DataFileReader<ModeSEncodedMessage> fileReader =
					new DataFileReader<ModeSEncodedMessage>(avro, datumReader);

			// stuff for handling flights
			ModeSEncodedMessage record = new ModeSEncodedMessage();
			HashMap<String, Flight> flights = new HashMap<String, Flight>();

			// temporary pointers
			Flight flight;
			String icao24;

			// message registers
			ModeSReply msg;
			AirbornePositionMsg airpos;
			VelocityOverGroundMsg velo;

			// for flight handling
			List<String> flights_to_remove = new ArrayList<String>();

			// for msg rate
			last_time = System.currentTimeMillis();
			while (fileReader.hasNext()) {
				// count messages
				msgs_cnt++;

				// print processing rate
				if (System.currentTimeMillis() - last_time > 1000) {
					System.err.format("\r%6d msgs/s", msgs_cnt-last_msgs_cnt);
					last_time = System.currentTimeMillis();
					last_msgs_cnt = msgs_cnt;
				}

				// get next record from file
				record = fileReader.next(record);

				// time filters
				if (filter_start != null && record.getTimeAtServer()<filter_start) {
					filtered_cnt++;
					continue;
				}
				if (filter_end != null && record.getTimeAtServer()>filter_end) {
					filtered_cnt++;
					continue;
				}

				// cleanup decoders every 1000000 messages to avoid excessive memory usage
				// therefore, remove decoders which have not been used for more than one hour.
				if (msgs_cnt%1000000 == 0) {
					for (String key : flights.keySet()) {
						if (flights.get(key).last<record.getTimeAtServer()-3600) {
							flights_to_remove.add(key);
						}
					}

					// remove and clear
					for (String key : flights_to_remove)
						flights.remove(key);
					flights_to_remove.clear();
				}

				try {
					msg = Decoder.genericDecoder(record.getRawMessage().toString());
				} catch (BadFormatException e) {
					continue;
				}

				Position rec = record.getSensorLatitude() != null ?
						new Position(
								record.getSensorLongitude(),
								record.getSensorLatitude(),
								record.getSensorAltitude()) : null;
				
				if (msg.getType() == subtype.ADSB_AIRBORN_POSITION) {
					icao24 = tools.toHexString(msg.getIcao24());

					// icao24 filter
					if (filter_icao24 != null && !icao24.equals(filter_icao24)) {
						filtered_cnt++;
						continue;
					}

					// select current flight
					if (flights.containsKey(icao24))
						flight = flights.get(icao24);
					else {
						// filter max flights
						if (filter_max != null && flights_cnt>filter_max) {
							filtered_cnt++;
							continue;
						}

						// new flight
						flight = a2sql.new Flight(flights_cnt);
						flights.put(icao24, flight);
						++flights_cnt;
					}

					flight.last = record.getTimeAtServer();

					airpos = (AirbornePositionMsg) msg;

					airpos.setNICSupplementA(flight.dec.getNICSupplementA());
					Position pos = flight.dec.decodePosition(record.getTimeAtServer(), rec, airpos);
					if (pos == null || !pos.isReasonable())
						++bad_pos_cnt;
					else if (pos.isReasonable()) {
						++good_pos_cnt;
						if (record.getSensorType().toString().equals("OpenSky") || record.getSensorType().toString().equals("Radarcape")) {
							a2sql.insertSensor(record.getSensorSerialNumber(), rec);
							a2sql.insertPosition(record.getSensorSerialNumber(), record.getTimeAtServer(),
									Math.round(record.getTimeAtSensor()), Math.round(record.getTimestamp()),
									pos, record.getRawMessage().toString());
						}
					}
				}
				else if (msg.getType() == subtype.ADSB_VELOCITY) {
					velo = (VelocityOverGroundMsg) msg;
					if (record.getSensorType().toString().equals("OpenSky") || record.getSensorType().toString().equals("Radarcape")) {
						a2sql.insertSensor(record.getSensorSerialNumber(), rec);
						a2sql.insertVelocity(record.getSensorSerialNumber(),  record.getTimeAtServer(),
								Math.round(record.getTimeAtSensor()), Math.round(record.getTimestamp()),
								velo.hasVelocityInfo() ? velo.getVelocity() : null,
								velo.hasVerticalRateInfo() ? velo.getVerticalRate() : null,
								velo.hasVelocityInfo() ? velo.getHeading() : null, 
								velo.hasGeoMinusBaroInfo() ? velo.getGeoMinusBaro() : null,
								record.getRawMessage().toString());
					}
				}
				// ignore any other message
				else ignored_cnt++;
			}

			a2sql.conn.commit();

			fileReader.close();

		} catch (IOException e) {
			// error while trying to read file
			System.err.println("IO Error: "+e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			// something went wrong
			System.err.println("Something went wrong: "+e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}

		System.err.println("\n\nStatistics:");
		System.err.format("\tTotal messages: %d\n", msgs_cnt);
		System.err.format("\tFiltered messages: %d\n", filtered_cnt);
		System.err.format("\tIgnored messages: %d\n", ignored_cnt);
		System.err.format("\tFlights: %d\n\n", flights_cnt);
		System.err.format("\tGood positions: %d\n", good_pos_cnt);
		System.err.format("\tBad positions: %d\n", bad_pos_cnt);
	}
}



