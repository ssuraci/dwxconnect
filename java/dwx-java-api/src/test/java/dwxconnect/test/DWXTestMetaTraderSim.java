package dwxconnect.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dwxconnect.api.Client;

// https://stackoverflow.com/a/62016201
public class DWXTestMetaTraderSim {

	protected boolean ACTIVE = true;
	protected boolean START = false;

	protected String watchDir;

	public enum FileType {
		ORDERS, MARKET_DATA, BAR_DATA, COMMAND_PREFIX, MESSAGE
	};

	private Logger logger = LoggerFactory.getLogger(Client.class);

	private Map<String, FileResponse> respMap;
	private Map<FileType, String> respFilePathMap = new TreeMap<>();

	private ObjectMapper objectMapper = new ObjectMapper();

	private Thread filePollThread;

	private String messageFilePath;
	
	public static class FileContentResponse {
		private Integer delay;
		private Map<String, Object> respContent;

		public Integer getDelay() {
			return delay;
		}

		public Map<String, Object> getRespContent() {
			return respContent;
		}
	}

	public static class DwxMessage {
		private String type;
		private String time;
		private String message;
		private Integer correlationId; 	
		
		public String getType() {
			return type;
		}
		public String getTime() {
			return time;
		}
		public String getMessage() {
			return message;
		}
		public Integer getCorrelationId() {
			return correlationId;
		}
		public DwxMessage(String type, String time, String message, Integer correlationId) {
			super();
			this.type = type;
			this.time = time;
			this.message = message;
			this.correlationId = correlationId;
		}
		public DwxMessage() {
		}
		
		
	}
	
	// "52566418_1": {"type": "INFO", "time": "2022.10.13 21:31:46", "message": "Successfully subscribed to: EURUSD, GBPUSD"}
	
	public static class FileResponse {
		private String command;
		private FileType respFileType;
		private Map<String, DwxMessage> messageFile = new TreeMap<>();
		private List<FileContentResponse> respFileList = new ArrayList<>();

		public String getCommand() {
			return command;
		}

		public FileType getRespFileType() {
			return respFileType;
		}

		public List<FileContentResponse> getRespFileList() {
			return respFileList;
		}

		public Map<String, DwxMessage> getMessageFile() {
			return messageFile;
		}

		public void setMessageFile(Map<String, DwxMessage> messageFile) {
			this.messageFile = messageFile;
		}


		
	}

	public DWXTestMetaTraderSim(String watchDir, String respJsonFile) throws IOException {
		this.watchDir = watchDir;
		Files.createDirectories(Paths.get(watchDir));
		FileUtils.cleanDirectory(new File(watchDir)); 
		List<FileResponse> respList = objectMapper.readValue(
				Paths.get("src/test/resources/json/" + respJsonFile).toFile(), new TypeReference<List<FileResponse>>() {
				});
		this.respMap = respList.stream().collect(Collectors.toMap(FileResponse::getCommand, Function.identity()));
		respFilePathMap.put(FileType.MARKET_DATA, Client.marketDataFileName);
		respFilePathMap.put(FileType.MESSAGE, Client.messagesFileName);
	}

	public void start() {
		messageFilePath = watchDir + "/"
				+ Optional.ofNullable(respFilePathMap.get(FileType.MESSAGE)).orElseThrow();
		filePollThread = new Thread(() -> filePoll());
		filePollThread.start();
		START = true;
	}

	private void filePoll() {
		logger.info("DWX MetaTrader Test Simulator started with path: " + watchDir);
		WatchService watchService;
		Path path = Paths.get(watchDir);
		try {
			watchService = FileSystems.getDefault().newWatchService();
			path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
		} catch (IOException e) {
			logger.error(e.getMessage());
			return;
		}
		WatchKey key;
		while (ACTIVE) {
			try {
				key = watchService.poll(100, TimeUnit.MILLISECONDS);
				if (!START || key == null) {
					continue;
				}
				for (WatchEvent<?> event : key.pollEvents()) {
					// logger.info("Event kind:" + event.kind() + ". File affected: " + event.context() + ".");
					if (event.context().toString().startsWith(Client.commandsPrefixFileName)) {
						String command = Files.readString(Paths.get(watchDir + "/" + event.context().toString()));
						String correlationId = null;
						if (command.length() > 0 && command.endsWith("#")) {
							correlationId=command.substring(command.indexOf(":>")+2, command.length()-1);
							command=command.substring(0, command.indexOf(":>")+2);
						}
						if (respMap.containsKey(command)) {
							FileResponse fileResponse = respMap.get(command);
							String filePath = watchDir + "/"
									+ Optional.ofNullable(respFilePathMap.get(fileResponse.respFileType)).orElseThrow();
														
							if (fileResponse.getMessageFile() != null) {
								Map<String, Object> correlatedMessageMap = new TreeMap<>();
								for (Entry<String, DwxMessage> entry : fileResponse.getMessageFile().entrySet()) {
									correlatedMessageMap.put(entry.getKey().replaceAll("CORRELATION_ID", correlationId), entry.getValue());
								}
								objectMapper.writeValue(new File(messageFilePath), correlatedMessageMap);
							}
							
							for (FileContentResponse fileResponseContent : fileResponse.respFileList) {
								if (fileResponseContent.delay > 0) {
									Thread.sleep(fileResponseContent.delay);
								}
								objectMapper.writeValue(new File(filePath), fileResponseContent.respContent);
							}
						} else {
							logger.warn("Unmapped command:" + command);
						}
					}
				}
				key.reset();
			} catch (IOException | InterruptedException e) {
				logger.error(e.getMessage());
			}
		}
	}

	public void stop() throws InterruptedException {
		logger.info("DWX MetaTrader Test Simulator stopping ... ");

		START = ACTIVE = false;
		filePollThread.join();
		logger.info("DWX MetaTrader Test Simulator stopped");
	}

}