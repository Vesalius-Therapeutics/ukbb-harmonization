package com.ukbb.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;

public class GzipPrintWriter extends Writer {

	FileOutputStream fos;
	GZIPOutputStream gzipOS;

	public GzipPrintWriter(String filename) {

		try {
			fos = new FileOutputStream(filename);
			gzipOS = new GZIPOutputStream(fos);
		} catch (IOException e) {
			System.err.println("Error creating GzipPrintWriter: " + e.toString());
		}
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		// Convert to a string and then write out the bytes
		String data = new String(cbuf, off, len);
		gzipOS.write(data.getBytes());
	}

	
	public void print(String data) throws IOException {
		gzipOS.write(data.getBytes());
	}
	
	public void println(String data) throws IOException {
		// append new line
		data = (data + "\n");
		gzipOS.write(data.getBytes());
	}

	
	@Override
	public void flush() throws IOException {
		gzipOS.flush();
	}

	@Override
	public void close() throws IOException {

		// close resources
		gzipOS.close();
		fos.close();
		return;

	}

}
