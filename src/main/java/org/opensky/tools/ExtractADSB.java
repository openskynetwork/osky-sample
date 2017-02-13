package org.opensky.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensky.avro.v2.ModeSEncodedMessage;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.ModeSReply.subtype;

/**
 * Read (possibly multiple) OpenSky Avro files, filter ADS-B messages only and
 * store the residual messages to one output file. This tool is useful if you
 * are only interested in ADS-B and want to get rid of everthing else first to have
 * less computational/storage overhead.
 * 
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 *
 */
public class ExtractADSB {

	/**
	 * Prints help for command line options
	 * @param opts command line options
	 */
	private static void printHelp(Options opts) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(
				"ExtractADSB [options/filters] input1 [input2 ...] -o output",
				"\nExtract ADS-B messages from OpenSky AVROs\nhttp://www.opensky-network.org\n\n",
				opts, "");
	}

	public static void main(String[] args) {

		// define command line options
		Options opts = new Options();
		opts.addOption("h", "help", false, "print this message" );
		opts.addOption("o", "output", true, "path and filename of output file");
		opts.addOption("p", "no-position", false, "ignore position messages");
		opts.addOption("v", "no-velocity", false, "ignore velocity messages");
		opts.addOption("i", "no-id", false, "ignore identification messages (callsign)");
		opts.addOption("b", "basic-only", false, "ignore everything but position, velocity and identification msgs.");

		// parse command line options
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
		String outpath = null;
		List<String> inpaths = null;
		boolean filter_pos = false, filter_vel = false,
				filter_id = false, filter_misc = false;
		try {
			cmd = parser.parse(opts, args);

			// print help
			if (cmd.hasOption("h")) {
				printHelp(opts);
				System.exit(0);
			}

			filter_pos = cmd.hasOption("p");
			filter_vel = cmd.hasOption("v");
			filter_id = cmd.hasOption("i");
			filter_misc = cmd.hasOption("b");

			// parse arguments
			try {
				if (cmd.hasOption("o")) outpath = cmd.getOptionValue("o");
				else throw new ParseException("Need output file!");
			} catch (NumberFormatException e) {
				throw new ParseException("Invalid output file: "+e.getMessage());
			}

			// get filename
			if (cmd.getArgList().size() == 0)
				throw new ParseException("Input files are missing!");
			inpaths = cmd.getArgList();
		} catch (ParseException e) {
			// parsing failed
			System.err.println(e.getMessage()+"\n");
			printHelp(opts);
			System.exit(1);
		}

		File out = new File(outpath);
		List<File> avroin = new ArrayList<File>();
		try {
			// check if output paths exist
			if (out.exists() && !out.isDirectory())
				throw new IOException("Output file already exists.");

			// check input files
			File tmp;
			for (String path : inpaths) {
				tmp = new File(path);
				if(!tmp.exists() || tmp.isDirectory() || !tmp.canRead())
					throw new FileNotFoundException("Avro file not found or cannot be read.");
				avroin.add(tmp);
			}
		} catch (IOException e) {
			System.err.println("Error: "+e.getMessage()+"\n");
			System.exit(1);
		}

		// AVRO file reader
		DatumReader<ModeSEncodedMessage> datumReader =
				new SpecificDatumReader<ModeSEncodedMessage>(ModeSEncodedMessage.class);
		// AVRO file writer
		DatumWriter<ModeSEncodedMessage> datumWriter =
				new SpecificDatumWriter<ModeSEncodedMessage>(ModeSEncodedMessage.class);

		// some counters for statistics
		long[] in_cnt = new long[avroin.size()];
		long out_cnt = 0, msgs_cnt = 0, filtered_cnt = 0;
		try {
			// open output files
			DataFileWriter<ModeSEncodedMessage> writer = new DataFileWriter<ModeSEncodedMessage>(datumWriter);
			writer.create(ModeSEncodedMessage.getClassSchema(), out);

			long last_time = System.currentTimeMillis(), last_msgs_cnt = 0;
			DataFileReader<ModeSEncodedMessage> fileReader;
			ModeSReply reply = null;
			// iterate over input files
			for (int i = 0; i<avroin.size(); ++i) {
				System.err.format("\nOpening %s.\n", inpaths.get(i));
				fileReader = new DataFileReader<ModeSEncodedMessage>(avroin.get(i), datumReader);
				while (fileReader.hasNext()) {
					// count messages
					++msgs_cnt;
					++in_cnt[i];

					// print processing rate
					if (System.currentTimeMillis() - last_time > 1000) {
						System.err.format("\r%6d msgs/s", msgs_cnt-last_msgs_cnt);
						last_time = System.currentTimeMillis();
						last_msgs_cnt = msgs_cnt;
					}

					// get next record from file
					ModeSEncodedMessage record = fileReader.next();
					try {
						reply = Decoder.genericDecoder(record.getRawMessage().toString());
					} catch (BadFormatException e) {
						System.err.println("\nSkipped bad formatted messages.");
					}
					
					if (reply.getDownlinkFormat() != 17 ||
							reply.getType() == subtype.EXTENDED_SQUITTER) { // no or unknown ADS-B
						++filtered_cnt;
						continue;
					}
					
					if (filter_pos && (reply.getType() == subtype.ADSB_AIRBORN_POSITION
							|| reply.getType() == subtype.ADSB_SURFACE_POSITION)) {
						++filtered_cnt;
						continue;
					}
					
					if (filter_vel && (reply.getType() == subtype.ADSB_VELOCITY
							|| reply.getType() == subtype.ADSB_AIRSPEED)) {
						++filtered_cnt;
						continue;
					}

					if (filter_id && reply.getType() == subtype.ADSB_IDENTIFICATION) {
						++filtered_cnt;
						continue;
					}
					
					if (filter_misc && (reply.getType() == subtype.ADSB_EMERGENCY
							|| reply.getType() == subtype.ADSB_STATUS
							|| reply.getType() == subtype.ADSB_TCAS)) {
						++filtered_cnt;
						continue;
					}
					
					++out_cnt;
					writer.append(record);
				}
				fileReader.close();
			}
			// close file
			writer.close();
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
		System.err.format("\tWritten messages: %d\n", out_cnt);
		System.err.format("\tFiltered messages: %d\n", filtered_cnt);
		System.err.println("\tCounts per input file:");
		for (int i=0; i<inpaths.size(); ++i)
			System.err.format("\t\t%s: %d\n", inpaths.get(i), in_cnt[i]);
	}
}
