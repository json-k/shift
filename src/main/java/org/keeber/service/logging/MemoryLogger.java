package org.keeber.service.logging;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class MemoryLogger {

	@WebServlet("/logging/*")
	public static class Service extends HttpServlet {

		@Override
		protected void service(final HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			response.getWriter().append(((MemoryLogger) getServletContext().getAttribute(MemoryLogger.SERVLETCONTEXT_REF)).asJSON()).close();
		}

	}

	public static final String SERVLETCONTEXT_REF = "MemoryLogger";

	private Gson serializer = null;

	private final Map<String, MemoryHandler> data = new LinkedHashMap<String, MemoryHandler>();
	private transient static SimpleFormatter formatter = new SimpleFormatter();

	public Handler addHandler(String name) {
		data.put(name, new MemoryHandler(name));
		return data.get(name).asHandler();
	}

	public Handler addHandler(String name, int size) {
		if (!data.containsKey(name)) {
			data.put(name, new MemoryHandler(name, size));
		}
		data.get(name).size = size;
		return data.get(name).asHandler();
	}

	public void removeHandler(String name) {
		synchronized (data) {
			data.remove(name);
		}
	}

	public void clearHandlers() {
		synchronized (data) {
			data.clear();
		}
	}

	public static MemoryLogger create(ServletContext context) {
		MemoryLogger logger = new MemoryLogger();
		context.setAttribute(SERVLETCONTEXT_REF, logger);
		return logger;
	}

	public class MemoryHandler {
		private final List<LoggerEntry> logs = new LinkedList<LoggerEntry>();
		private transient Engine engine = new Engine();
		private transient int size = 300;
		private String name;
		private String id;

		public MemoryHandler(String name, int size) {
			this.size = size;
			this.name = name;
			init();
		}

		public MemoryHandler(String name) {
			this.name = name;
			init();
		}

		private void init() {
			UUID uuid = UUID.nameUUIDFromBytes(this.name.getBytes());
			id = "ID" + uuid.toString().replaceAll("[^0-9A-Za-z]", "");
		}

		public String getName() {
			return name;
		}

		public String getId() {
			return this.id;
		}

		public void clear() {
			synchronized (logs) {
				logs.clear();
			}
		}

		public Handler asHandler() {
			return engine;
		}

		private class Engine extends Handler {

			@Override
			public void publish(LogRecord record) {
				synchronized (logs) {
					logs.add(new LoggerEntry(record));
					while (logs.size() > size) {
						logs.remove(0);
					}
				}
			}

			@Override
			public void close() throws SecurityException {

			}

			@Override
			public void flush() {

			}
		}

	}

	public static class LoggerEntry {
		@SuppressWarnings("unused")
		private String id;
		@SuppressWarnings("unused")
		private String message;

		protected LoggerEntry(LogRecord record) {
			id = "LOG" + record.getSequenceNumber();
			message = formatter.format(record).replaceAll("\r", "").trim();
		}
	}

	public String asJSON() {
		if (serializer == null) {
			serializer = new GsonBuilder().setPrettyPrinting().create();
		}
		try {
			return serializer.toJson(this.data.values(), new TypeToken<Collection<MemoryHandler>>() {
			}.getType());
		} catch (Exception e) {
			return "{}";
		}
	}

}
