package net.happybrackets.intellij_plugin;

import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.sun.javafx.css.Style;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.*;
import javafx.stage.Popup;
import javafx.util.Callback;
import javafx.util.Duration;
import net.happybrackets.controller.config.ControllerConfig;
import net.happybrackets.controller.gui.DeviceRepresentationCell;
import net.happybrackets.controller.network.DeviceConnection;
import net.happybrackets.controller.network.LocalDeviceRepresentation;
import net.happybrackets.controller.network.SendToDevice;

import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sets up the plugin GUI and handles associated events.
 */
public class IntelliJPluginGUIManager {
	private String compositionsPath;
	private String currentCompositionSelection = null;
	private ControllerConfig config;
	private final Project project;
	private DeviceConnection deviceConnection;
	private ListView<LocalDeviceRepresentation> deviceListView;
	private ComboBox<String> compositionSelector;
	private Text compositionPathText;
	private List<String> commandHistory;
	private int positionInCommandHistory = 0;
	private Style style;
	private final int defaultElementSpacing = 10;
	private Button[] configApplyButton = new Button[2]; // 0 = overall config, 1 = known devices.
	final static Logger logger = LoggerFactory.getLogger(IntelliJPluginGUIManager.class);


	public IntelliJPluginGUIManager(Project project) {
		this.project = project;
		init();
		commandHistory = new ArrayList<>();
	}

	private void init() {
		config = HappyBracketsToolWindow.config;
		deviceConnection = HappyBracketsToolWindow.deviceConnection;
		//initial compositions path
		//assume that this path is a path to a root classes folder, relative to the project
		//e.g., build/classes/tutorial or build/classes/compositions
		compositionsPath = project.getBaseDir().getCanonicalPath() + "/" + config.getCompositionsPath();
	}



	public Scene setupGUI() {
		//core elements
		TitledPane configPane = new TitledPane("Configuration", makeConfigurationPane(0));
		TitledPane knownDevicesPane = new TitledPane("Known Devices", makeConfigurationPane(1));
		TitledPane globalPane = new TitledPane("Global Management", makeGlobalPane());
		TitledPane compositionPane = new TitledPane("Compositions and Commands", makeCompositionPane());
		TitledPane debugPane = new TitledPane("Debug", makeDebugPane());

		Accordion controlPane = new Accordion();
		controlPane.getPanes().addAll(configPane, knownDevicesPane, globalPane, compositionPane, debugPane);
		controlPane.setExpandedPane(compositionPane);

//		SplitPane mainSplit = new SplitPane();
//		mainSplit.setOrientation(Orientation.VERTICAL);
//		mainSplit.getItems().add(makeScrollPane(
//				makeTitle("Devices"),
//				makeDevicePane()
//		));
//		mainSplit.getItems().add(makeScrollPane(
//				makeTitle("Debug"),
//				makeDebugPane()
//		));
//		mainSplit.setPrefHeight(10000);
//
//		VBox mainContainer = new VBox(defaultElementSpacing);
//		mainContainer.getChildren().addAll(controlPane, new Separator(), mainSplit);

		VBox mainContainer = new VBox(5);
		mainContainer.setFillWidth(true);
		mainContainer.getChildren().addAll(controlPane, new Separator(), makeDevicePane());

		ScrollPane mainScroll = new ScrollPane();
		mainScroll.setFitToWidth(true);
		mainScroll.setFitToHeight(true);
		mainScroll.setStyle("-fx-font-family: sample; -fx-font-size: 12;");
		mainScroll.setMinHeight(100);
		mainScroll.setContent(mainContainer);


		//finally update composition path
		updateCompositionPath(compositionsPath);

		//return a JavaFX Scene
		return new Scene(mainScroll);
	}

//	private ScrollPane makeScrollPane(Node... items) {
//		VBox vbox = new VBox(defaultElementSpacing);
//		vbox.setPadding(new Insets(defaultElementSpacing));
//		vbox.setFillWidth(true);
//		vbox.setAlignment(Pos.TOP_CENTER);
//		vbox.getChildren().addAll(items);
//
//		ScrollPane scroll = new ScrollPane();
//		scroll.setPannable(true);
//		scroll.setFitToWidth(true);
//		scroll.setFitToHeight(true);
//		scroll.setContent(vbox);
//		return scroll;
//	}

	private Text makeTitle(String title) {
		Text text = new Text(title);
		text.setTextAlignment(TextAlignment.CENTER);
		text.setTextOrigin(VPos.CENTER);
		text.setStyle("-fx-font-weight: bold;");
		return text;
	}


	private Pane makeGlobalPane() {
		//master buttons
		FlowPane globalcommands = new FlowPane(10, 10);
		globalcommands.setAlignment(Pos.TOP_RIGHT);
		{
			Button b = new Button("Reboot");
			b.setOnMouseClicked(event -> deviceConnection.deviceReboot());
			b.setTooltip(new Tooltip("Reboot all devices."));
			globalcommands.getChildren().add(b);
		}
		{
			Button b = new Button("Shutdown");
			b.setOnMouseClicked(event -> deviceConnection.deviceShutdown());
			b.setTooltip(new Tooltip("Shutdown all devices."));
			globalcommands.getChildren().add(b);
		}
		{
			Button b = new Button("Reset");
			b.setOnMouseClicked(e -> deviceConnection.deviceReset());
			b.setTooltip(new Tooltip("Reset all devices to their initial state (same as Reset Sounding + Clear Sound)."));
			globalcommands.getChildren().add(b);
		}
		{
			Button b = new Button("Reset Sounding");
			b.setOnMouseClicked(e -> deviceConnection.deviceResetSounding());
			b.setTooltip(new Tooltip("Reset all devices to their initial state except for audio that is currently playing."));
			globalcommands.getChildren().add(b);
		}
		{
			Button b = new Button("Clear Sound");
			b.setOnMouseClicked(e -> deviceConnection.deviceClearSound());
			b.setTooltip(new Tooltip("Clears all of the audio that is currently playing on all devices."));
			globalcommands.getChildren().add(b);
		}
		return globalcommands;
	}

	/**
	 * Make Configuration/Known devices pane.
	 * @param fileType 0 == configuration, 1 == known devices.
	 */
	private Pane makeConfigurationPane(final int fileType) {
		final TextArea configField = new TextArea();
		final String label = fileType == 0 ? "Configuration" : "Known Devices";
		final String setting = fileType == 0 ? "controllerConfigPath" : "knownDevicesPath";

		configField.setPrefSize(400, 250);
		// Load initial config into text field.
		if (fileType == 0) {
			configField.setText(HappyBracketsToolWindow.getCurrentConfigString());
		}
		else {
			StringBuilder map = new StringBuilder();
			deviceConnection.getKnownDevices().forEach((hostname, id) -> map.append(hostname + " " + id + "\n"));
			configField.setText(map.toString());
		}
		configField.textProperty().addListener((observable, oldValue, newValue) -> {
			configApplyButton[fileType].setDisable(false);
		});

		Button loadButton = new Button("Load");
		loadButton.setTooltip(new Tooltip("Load a new " + label.toLowerCase() + " file."));
		loadButton.setOnMouseClicked(event -> {
			//select a file
			final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().withShowHiddenFiles(true);
			descriptor.setTitle("Select " + label.toLowerCase() + " file");

			String currentFile = HappyBracketsToolWindow.getSettings().getString(setting);
			VirtualFile vfile = currentFile == null ? null : LocalFileSystem.getInstance().findFileByPath(currentFile.replace(File.separatorChar, '/'));

			//needs to run in Swing event dispatch thread, and then back again to JFX thread!!
			SwingUtilities.invokeLater(() -> {
				VirtualFile[] virtualFile = FileChooser.chooseFiles(descriptor, null, vfile);
				if (virtualFile != null && virtualFile.length > 0 && virtualFile[0] != null) {
					Platform.runLater(() -> {
						loadConfigFile(virtualFile[0].getCanonicalPath(), label, configField, setting, loadButton, event);
					});
				}
			});
		});

		Button saveButton = new Button("Save");
		saveButton.setTooltip(new Tooltip("Save these " + label.toLowerCase() + " settings to a file."));
		saveButton.setOnMouseClicked(event -> {
			//select a file
            FileSaverDescriptor fsd = new FileSaverDescriptor("Select " + label.toLowerCase() + " file to save to.", "Select " + label.toLowerCase() + " file to save to.");
            fsd.withShowHiddenFiles(true);
			final FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(fsd, project);

            String currentFilePath = HappyBracketsToolWindow.getSettings().getString(setting);
            File currentFile = currentFilePath != null ? new File(HappyBracketsToolWindow.getSettings().getString(setting)) : null;
            VirtualFile baseDir = null;
            String currentName = null;
            if (currentFile != null && currentFile.exists()) {
                baseDir = LocalFileSystem.getInstance().findFileByPath(currentFile.getParentFile().getAbsolutePath().replace(File.separatorChar, '/'));
                currentName = currentFile.getName();
            }
            else {
                baseDir = LocalFileSystem.getInstance().findFileByPath(HappyBracketsToolWindow.getPluginLocation());
                currentName = fileType == 0 ? "controller-config.json" : "known_devices";
            }
            final VirtualFile baseDirFinal = baseDir;
            final String currentNameFinal = currentName;

			//needs to run in Swing event dispatch thread, and then back again to JFX thread!!
			SwingUtilities.invokeLater(() -> {
				final VirtualFileWrapper wrapper = dialog.save(baseDirFinal, currentNameFinal);

				if (wrapper != null) {
					Platform.runLater(() -> {
						File configFile = wrapper.getFile();

						// Check for overwrite of default config files (this doesn't apply to deployed plugin so disabling for now.)
						//if ((new File(HappyBracketsToolWindow.getDefaultControllerConfigPath())).getAbsolutePath().equals(configFile.getAbsolutePath()) ||
						//		(new File(HappyBracketsToolWindow.getDefaultKnownDevicesPath())).getAbsolutePath().equals(configFile.getAbsolutePath())) {
						//	showPopup("Error saving " + label.toLowerCase() + ": cannot overwrite default configuration files.", saveButton, 5, event);
						//}

						try (PrintWriter out = new PrintWriter(configFile.getAbsolutePath())) {
							out.print(configField.getText());

							HappyBracketsToolWindow.getSettings().set(setting, configFile.getAbsolutePath());
						} catch (Exception ex) {
							showPopup("Error saving " + label.toLowerCase() + ": " + ex.getMessage(), saveButton, 5, event);
						}
					});
				}
			});
		});

		Button resetButton = new Button("Reset");
		resetButton.setTooltip(new Tooltip("Reset these " + label.toLowerCase() + " settings to their defaults."));
		resetButton.setOnMouseClicked(event -> {
			HappyBracketsToolWindow.getSettings().clear(setting);

			if (fileType == 0) {
				loadConfigFile(HappyBracketsToolWindow.getDefaultControllerConfigPath(), label, configField, setting, resetButton, event);
				applyConfig(configField.getText());
			}
			else {
				loadConfigFile(HappyBracketsToolWindow.getDefaultKnownDevicesPath(), label, configField, setting, resetButton, event);
				applyKnownDevices(configField.getText());
			}
		});

		configApplyButton[fileType] = new Button("Apply");
		configApplyButton[fileType].setTooltip(new Tooltip("Apply these " + label.toLowerCase() + " settings."));
		configApplyButton[fileType].setDisable(true);
		configApplyButton[fileType].setOnMouseClicked(event -> {
			configApplyButton[fileType].setDisable(true);

			if (fileType == 0) {
				applyConfig(configField.getText());
			} else {
				applyKnownDevices(configField.getText());
			}
		});

		HBox buttons = new HBox(defaultElementSpacing);
		buttons.setAlignment(Pos.TOP_RIGHT);
		buttons.getChildren().addAll(loadButton, saveButton, resetButton, configApplyButton[fileType]);

		VBox configPane = new VBox(defaultElementSpacing);
		configPane.setAlignment(Pos.TOP_RIGHT);
		configPane.getChildren().addAll(makeTitle(label), configField, buttons);

		return configPane;
	}


	private void loadConfigFile(String path, String label, TextArea configField, String setting, Node triggeringElement, MouseEvent event) {
		File configFile = new File(path);
		try {
			String configJSON = (new Scanner(configFile)).useDelimiter("\\Z").next();
			configField.setText(configJSON);
			HappyBracketsToolWindow.getSettings().set(setting, configFile.getAbsolutePath());
		} catch (FileNotFoundException ex) {
			showPopup("Error loading " + label.toLowerCase() + ": " + ex.getMessage(), triggeringElement, 5, event);
		}
	}


	private void applyConfig(String config) {
		HappyBracketsToolWindow.setConfig(config, null);
		init();
		deviceListView.setItems(deviceConnection.getDevices());
		refreshCompositionList();
	}


	private void applyKnownDevices(String kd) {
		deviceConnection.setKnownDevices(kd.split("\\r?\\n"));
	}


	private Node makeCompositionPane() {
		VBox container = new VBox(defaultElementSpacing);
		container.getChildren().addAll(
				makeTitle("Composition folder"),
				makeCompositionFolderPane(),
				new Separator(),
				makeTitle("Send Composition"),
				makeCompositionSendPane(),
				new Separator(),
				makeTitle("Send Custom Command"),
				makeCustomCommandPane()
		);

        // Work around. On Mac the layout doesn't allow enough height in some instances.
		container.setMinHeight(275);

		return container;
	}

	private Pane makeCompositionFolderPane() {
		compositionPathText = new Text();
		TextFlow compositionPathTextPane = new TextFlow(compositionPathText);
		compositionPathTextPane.setTextAlignment(TextAlignment.RIGHT);

		Button changeCompositionPath = new Button("Change");
		changeCompositionPath.setTooltip(new Tooltip("Select a new folder containing composition files."));
		changeCompositionPath.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				//select a folder
				final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
				descriptor.setTitle("Select Composition Folder");
				//needs to run in Swing event dispatch thread, and then back again to JFX thread!!
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						VirtualFile[] virtualFile = FileChooser.chooseFiles(descriptor, null, null);
						if (virtualFile != null && virtualFile.length > 0 && virtualFile[0] != null) {
							Platform.runLater(new Runnable() {
								@Override
								public void run() {
									updateCompositionPath(virtualFile[0].getCanonicalPath());
								}
							});
						}
					}
				});
			}
		});
		Button refreshButton = new Button("Refresh");
		refreshButton.setTooltip(new Tooltip("Reload the available composition files."));
		refreshButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				refreshCompositionList();
			}
		});

		FlowPane compositionFolderPane = new FlowPane(10, 10);
		compositionFolderPane.setAlignment(Pos.TOP_RIGHT);
		compositionFolderPane.getChildren().addAll(compositionPathText, changeCompositionPath, refreshButton);

		return compositionFolderPane;
	}

	private Pane makeCompositionSendPane() {
		// Create the ComboBox containing the compoositions
		compositionSelector = new ComboBox<String>();
//		compositionSelector.setMaxWidth(200);
		compositionSelector.setTooltip(new Tooltip("Select a composition file to send."));
		compositionSelector.setPrefWidth(200);
		compositionSelector.setButtonCell(
				new ListCell<String>() {
					  {
						  super.setPrefWidth(100);
					  }

					  @Override
					  protected void updateItem(String item, boolean empty) {
						  super.updateItem(item, empty);
						  if (item != null) {
							  String[] parts = item.split("/");
							  if (parts.length == 0) {
								  setText(item);
							  } else {
								  setText(parts[parts.length - 1]);
							  }
						  }
					  }
				  }
		);
		compositionSelector.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, final String arg2) {
				if (arg2 != null) {
					currentCompositionSelection = arg2; //re-attach the composition path to the compositionSelector item name
				}
			}
		});

		Button compositionSendButton = new Button("All");
		compositionSendButton.setTooltip(new Tooltip("Send the selected composition to all devices."));
		compositionSendButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent e) {
				if (currentCompositionSelection != null) {
					//intelliJ specific code
					String pathToSend = compositionsPath + "/" + currentCompositionSelection;
					try {
						SendToDevice.send(pathToSend, deviceConnection.getDeviceHostnames());
					} catch (Exception ex) {
						logger.error("Unable to send composition: '{}'!", pathToSend, ex);
					}
				}
			}
		});

		HBox compositionSendPane = new HBox(defaultElementSpacing);
		compositionSendPane.getChildren().addAll(compositionSelector, compositionSendButton);
		return compositionSendPane;
	}

	private Pane makeCustomCommandPane() {
		final TextField codeField = new TextField();
		codeField.setTooltip(new Tooltip("Enter a custom command to send."));
		codeField.setPrefSize(500, 40);
		codeField.setOnKeyPressed(new EventHandler<KeyEvent>() {
			@Override
			public void handle(KeyEvent event) {
				if(event.getEventType() == KeyEvent.KEY_PRESSED) {
					if(event.getCode() == KeyCode.UP) {
						positionInCommandHistory--;
						if(positionInCommandHistory < 0) positionInCommandHistory = 0;
						if(commandHistory.size() > 0) {
							String command = commandHistory.get(positionInCommandHistory);
							if (command != null) {
								codeField.setText(command);
							}
						}
					} else if(event.getCode() == KeyCode.DOWN) {
						positionInCommandHistory++;
						if(positionInCommandHistory >= commandHistory.size()) positionInCommandHistory = commandHistory.size() - 1;
						if(commandHistory.size() > 0) {
							String command = commandHistory.get(positionInCommandHistory);
							if (command != null) {
								codeField.setText(command);
							}
						}
					} else if(!event.getCode().isModifierKey() && !event.getCode().isNavigationKey()){
						//nothing needs to be done here but I thought it'd be cool to have a comment in an if block.
					}
				}
			}
		});

		HBox messagepaths = new HBox(defaultElementSpacing);
		messagepaths.setAlignment(Pos.TOP_RIGHT);
		Button sendAllButton = new Button("All");
		sendAllButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent e) {
				sendCustomCommand(codeField.getText(), true, 0);
			}
		});
		messagepaths.getChildren().add(sendAllButton);
		for(int i = 0; i < 4; i++) {
			Button b = new Button();
			final int index = i;
			b.setOnMouseClicked(new EventHandler<MouseEvent>() {
				@Override
				public void handle(MouseEvent e) {
					sendCustomCommand(codeField.getText(), false, index);
				}
			});
			b.setText("" + (i + 1));
			messagepaths.getChildren().add(b);
		}

		VBox customCommandPane = new VBox(defaultElementSpacing);
		customCommandPane.getChildren().addAll(codeField, messagepaths);
		return customCommandPane;
	}


	/**
	 * Send a custom command to the specified devices.
	 * @param text The command to send.
	 * @param all Set to true to send the command to all devices.
	 * @param group If 'all' is false the group of devices to send the command to.
	 */
	public void sendCustomCommand(String text, boolean all, int group) {
		String codeText = text.trim();
		commandHistory.add(codeText);
		positionInCommandHistory = commandHistory.size() - 1;
		//need to parse the code text
		String[] commands = codeText.split("[;]");	//different commands separated by ';'
		for(String command : commands) {
			command = command.trim();
			String[] elements = command.split("[ ]");
			String msg = elements[0];
			Object[] args = new Object[elements.length - 1];
			for (int i = 0; i < args.length; i++) {
				String s = elements[i + 1];
				try {
					args[i] = Integer.parseInt(s);
				} catch (Exception ex) {
					try {
						args[i] = Double.parseDouble(s);
					} catch (Exception exx) {
						args[i] = s;
					}
				}
			}
			if(all) {
				deviceConnection.sendToAllDevices(msg, args);
			} else {
				deviceConnection.sendToDeviceGroup(group, msg, args);
			}
		}
	}


	private void updateCompositionPath(String path) {
		//TODO this needs to be saved somewhere project-specific
		compositionsPath = path;
		compositionPathText.setText(compositionsPath);
		//write the config file again
		refreshCompositionList();
	}

	private void refreshCompositionList() {
		logger.debug("refreshCompositionList: compositionsPath={}", compositionsPath);
		//TODO set up the project so that it auto-compiles and auto-refreshes on file save/edit.
		//locate the class files of composition classes
		//the following populates a list of Strings with class files, associated with compositions
		//populate combobox with list of compositions
		List<String> compositionFileNames = new ArrayList<String>();
		recursivelyGatherCompositionFileNames(compositionFileNames, compositionsPath);
		compositionSelector.getItems().clear();
		for(final String compositionFileName : compositionFileNames) {
			compositionSelector.getItems().add(compositionFileName);
		}
		if(compositionFileNames.size() > 0) {
			//if there was a current dynamoAction, grab it
			if (!compositionSelector.getItems().contains(currentCompositionSelection)) {
				currentCompositionSelection = compositionFileNames.get(0);
			}
			compositionSelector.setValue(currentCompositionSelection);
		} else {
			currentCompositionSelection = null;
		}
	}

	private void recursivelyGatherCompositionFileNames(List<String> compositionFileNames, String currentDir) {
		//TODO best approach would be to examine code source tree, then we can gather dependencies properly as well
		//scan the current dir for composition files
		//drop into any folders encountered
		//add any file that looks like a composition file (is a top-level class)
		String[] contents = new File(currentDir).list();
		if(contents != null) {
			for(String item : contents) {
				item = currentDir + "/" + item;
				File f = new File(item);
				if(f.isDirectory()) {
					recursivelyGatherCompositionFileNames(compositionFileNames, item);
				} else if(f.isFile()) {
					if(item.endsWith(".class") && !item.contains("$")) {
						item = item.substring(compositionsPath.length() + 1, item.length() - 6);
						// 6 equates to the length fo the .class extension, the + 1 is to remove the composition path and trailing '/' for presentation in the compositionSelector
						compositionFileNames.add(item);
					}
				}
			}
		}
	}


	private Node makeDevicePane() {
		//list of Devices
		deviceListView = new ListView<LocalDeviceRepresentation>();
		deviceListView.setItems(deviceConnection.getDevices());
		deviceListView.setCellFactory(new Callback<ListView<LocalDeviceRepresentation>, ListCell<LocalDeviceRepresentation>>() {
			@Override
			public ListCell<LocalDeviceRepresentation> call(ListView<LocalDeviceRepresentation> theView) {
				return new DeviceRepresentationCell();
			}
		});
		deviceListView.setMinHeight(50);

		VBox pane = new VBox(defaultElementSpacing);
		pane.setPadding(new Insets(defaultElementSpacing));
		pane.getChildren().addAll(makeTitle("Devices"), deviceListView);

		return pane;
	}


	private Node makeDebugPane() {
		VBox pane = new VBox(defaultElementSpacing);
		pane.setMinHeight(50);
		return pane;
	}


	private void showPopup(String message, Node element, int timeout, MouseEvent event) {
		Text t = new Text(message);

		VBox pane = new VBox();
		pane.setPadding(new Insets(10));
		pane.getChildren().add(t);

		Popup p = new Popup();
		p.getScene().setFill(Color.ORANGE);
		p.getContent().add(pane);
		p.show(element, event.getScreenX(), event.getScreenY());
		p.setAutoHide(true);

		if (timeout >= 0) {
			PauseTransition pause = new PauseTransition(Duration.seconds(timeout));
			pause.setOnFinished(e -> p.hide());
			pause.play();
		}
	}
}
