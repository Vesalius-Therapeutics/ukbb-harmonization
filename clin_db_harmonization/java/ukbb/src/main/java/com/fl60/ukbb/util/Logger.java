package com.fl60.ukbb.util;

public class Logger {

	public void log(String msg) {
		msg = new java.util.Date().toString() + " - " + msg;
		System.out.println(msg);
	}

	public void error(String msg) {
		msg = new java.util.Date().toString() + " - " + msg;
		System.err.println(msg);
	}
}
