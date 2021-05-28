/*
  MqttTabController.java

  Firefly Luciferin, very fast Java Screen Capture software designed
  for Glow Worm Luciferin firmware.

  Copyright (C) 2020 - 2021  Davide Perini

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package org.dpsoftware.gui;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import lombok.extern.slf4j.Slf4j;
import org.dpsoftware.FireflyLuciferin;
import org.dpsoftware.NativeExecutor;
import org.dpsoftware.audio.AudioLoopback;
import org.dpsoftware.audio.AudioLoopbackSoftware;
import org.dpsoftware.audio.AudioUtility;
import org.dpsoftware.config.Configuration;
import org.dpsoftware.config.Constants;
import org.dpsoftware.managers.MQTTManager;
import org.dpsoftware.managers.dto.ColorDto;
import org.dpsoftware.managers.dto.GammaDto;
import org.dpsoftware.managers.dto.StateDto;
import org.dpsoftware.utilities.CommonUtility;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Misc Tab controller
 */
@Slf4j
public class MiscTabController {

    // Inject main controller
    @FXML private SettingsController settingsController;
    // FXML binding
    @FXML private Label contextChooseColorChooseLoopback;
    @FXML public ColorPicker colorPicker;
    @FXML public ToggleButton toggleLed;
    @FXML public CheckBox startWithSystem;
    @FXML public ComboBox<String> framerate;
    @FXML public Slider brightness;
    @FXML private Label contextGammaGain;
    @FXML public ComboBox<String> gamma;
    @FXML public ComboBox<String> whiteTemperature;
    @FXML public ComboBox<String> effect;
    @FXML public Slider audioGain;
    @FXML public ComboBox<String> audioChannels;
    @FXML public ComboBox<String> audioDevice;
    @FXML public CheckBox eyeCare;
    @FXML public Spinner<LocalTime> nightModeFrom;
    @FXML public Spinner<LocalTime> nightModeTo;
    @FXML public Spinner<String> nightModeBrightness;
    @FXML public Button saveMiscButton;
    @FXML RowConstraints runLoginRow;
    @FXML Label runAtLoginLabel;

    /**
     * Inject main controller containing the TabPane
     * @param settingsController TabPane controller
     */
    public void injectSettingsController(SettingsController settingsController) {
        this.settingsController = settingsController;
    }

    /**
     * Initialize controller with system's specs
     */
    @FXML
    protected void initialize() {

        if (NativeExecutor.isLinux()) {
            runLoginRow.setPrefHeight(0);
            runLoginRow.setMinHeight(0);
            runLoginRow.setPercentHeight(0);
            runAtLoginLabel.setVisible(false);
            startWithSystem.setVisible(false);
        }
        audioDevice.getItems().add(Constants.DEFAULT_AUDIO_OUTPUT);
        if (FireflyLuciferin.config != null && AudioLoopback.audioDevices.isEmpty()) {
            AudioUtility audioLoopback = new AudioLoopbackSoftware();
            for (String device : audioLoopback.getLoopbackDevices().values()) {
                if (device.contains(Constants.LOOPBACK)) audioDevice.getItems().add(device);
            }
        } else {
            for (String device : AudioLoopback.audioDevices.values()) {
                if (device.contains(Constants.LOOPBACK)) audioDevice.getItems().add(device);
            }
        }
        for (Constants.Framerate fps : Constants.Framerate.values()) {
            framerate.getItems().add(fps.getFramerate());
        }

    }

    /**
     * Init combo boxes
     */
    void initComboBox() {

        for (Constants.Gamma gma : Constants.Gamma.values()) {
            gamma.getItems().add(gma.getGamma());
        }
        for (Constants.Effect ef : Constants.Effect.values()) {
            effect.getItems().add(ef.getEffect());
        }
        for (Constants.WhiteTemperature kelvin : Constants.WhiteTemperature.values()) {
            whiteTemperature.getItems().add(kelvin.getWhiteTemperature());
        }
        for (Constants.AudioChannels audioChan : Constants.AudioChannels.values()) {
            audioChannels.getItems().add(audioChan.getAudioChannels());
        }

    }

    /**
     * Init form values
     */
    void initDefaultValues() {

        gamma.setValue(Constants.GAMMA_DEFAULT);
        whiteTemperature.setValue(Constants.WhiteTemperature.UNCORRECTEDTEMPERATURE.getWhiteTemperature());
        effect.setValue(Constants.Effect.BIAS_LIGHT.getEffect());
        framerate.setValue("30 FPS");
        toggleLed.setSelected(true);
        brightness.setValue(255);
        audioGain.setVisible(false);
        audioDevice.setVisible(false);
        audioChannels.setVisible(false);
        audioChannels.setValue(Constants.AudioChannels.AUDIO_CHANNEL_2.getAudioChannels());
        audioDevice.setValue(Constants.DEFAULT_AUDIO_OUTPUT);
        WidgetFactory widgetFactory = new WidgetFactory();
        nightModeFrom.setValueFactory(widgetFactory.timeSpinnerValueFactory(LocalTime.now().withHour(22).withMinute(0).truncatedTo(ChronoUnit.MINUTES)));
        nightModeTo.setValueFactory(widgetFactory.timeSpinnerValueFactory(LocalTime.now().withHour(8).withMinute(0).truncatedTo(ChronoUnit.MINUTES)));
        nightModeBrightness.setValueFactory(widgetFactory.spinnerNightModeValueFactory());
        enableDisableNightMode(Constants.NIGHT_MODE_OFF);

    }

    /**
     * Toggle night mode params
     * @param nightModeBrightness brightness param for night mode
     */
    public void enableDisableNightMode(String nightModeBrightness) {

        if (nightModeBrightness.equals(Constants.NIGHT_MODE_OFF)) {
            nightModeFrom.setDisable(true);
            nightModeTo.setDisable(true);
        } else {
            nightModeFrom.setDisable(false);
            nightModeTo.setDisable(false);
        }

    }

    /**
     * Init form values by reading existing config file
     * @param currentConfig stored config
     */
    public void initValuesFromSettingsFile(Configuration currentConfig) {

        if (NativeExecutor.isWindows()) {
            startWithSystem.setSelected(currentConfig.isStartWithSystem());
        }
        gamma.setValue(String.valueOf(currentConfig.getGamma()));
        whiteTemperature.setValue(Constants.WhiteTemperature.values()[currentConfig.getWhiteTemperature()-1].getWhiteTemperature());
        framerate.setValue(currentConfig.getDesiredFramerate() + ((currentConfig.getDesiredFramerate().equals(Constants.UNLOCKED)) ? "" : " FPS"));
        eyeCare.setSelected(currentConfig.isEyeCare());
        String[] color = (FireflyLuciferin.config.getColorChooser().equals(Constants.DEFAULT_COLOR_CHOOSER)) ?
                currentConfig.getColorChooser().split(",") : FireflyLuciferin.config.getColorChooser().split(",");
        colorPicker.setValue(Color.rgb(Integer.parseInt(color[0]), Integer.parseInt(color[1]), Integer.parseInt(color[2]), Double.parseDouble(color[3])/255));
        brightness.setValue((Double.parseDouble(color[3])/255)*100);
        audioGain.setValue(currentConfig.getAudioLoopbackGain());
        audioChannels.setValue(currentConfig.getAudioChannels());
        audioDevice.setValue(currentConfig.getAudioDevice());
        effect.setValue(FireflyLuciferin.config.getEffect());
        if (FireflyLuciferin.config.isToggleLed()) {
            toggleLed.setText(Constants.TURN_LED_OFF);
        } else {
            toggleLed.setText(Constants.TURN_LED_ON);
        }
        toggleLed.setSelected(FireflyLuciferin.config.isToggleLed());
        WidgetFactory widgetFactory = new WidgetFactory();
        nightModeFrom.setValueFactory(widgetFactory.timeSpinnerValueFactory(FireflyLuciferin.config.getNightModeFrom()));
        nightModeTo.setValueFactory(widgetFactory.timeSpinnerValueFactory(FireflyLuciferin.config.getNightModeTo()));
        nightModeBrightness.setValueFactory(widgetFactory.spinnerNightModeValueFactory());
        enableDisableNightMode(nightModeBrightness.getValue());

    }

    /**
     * Setup the context menu based on the selected effect
     */
    public void setContextMenu() {

        if (Constants.Effect.MUSIC_MODE_VU_METER.getEffect().equals(FireflyLuciferin.config.getEffect())
                || Constants.Effect.MUSIC_MODE_BRIGHT.getEffect().equals(FireflyLuciferin.config.getEffect())
                || Constants.Effect.MUSIC_MODE_RAINBOW.getEffect().equals(FireflyLuciferin.config.getEffect()))  {
            colorPicker.setVisible(false);
            contextChooseColorChooseLoopback.setText(Constants.CONTEXT_MENU_AUDIO_DEVICE);
            gamma.setVisible(false);
            contextGammaGain.setText(Constants.CONTEXT_MENU_AUDIO_GAIN);
            audioGain.setVisible(true);
            audioDevice.setVisible(true);
            audioChannels.setVisible(true);
        } else {
            colorPicker.setVisible(true);
            contextChooseColorChooseLoopback.setText(Constants.CONTEXT_MENU_COLOR);
            gamma.setVisible(true);
            contextGammaGain.setText(Constants.CONTEXT_MENU_GAMMA);
            audioGain.setVisible(false);
            audioDevice.setVisible(false);
            audioChannels.setVisible(false);
        }

    }

    /**
     * Send serialParams, this will cause a reboot on the microcontroller
     */
    void sendSerialParams() {

        java.awt.Color[] leds = new java.awt.Color[1];
        try {
            leds[0] = new java.awt.Color((int)(colorPicker.getValue().getRed() * 255),
                    (int)(colorPicker.getValue().getGreen() * 255),
                    (int)(colorPicker.getValue().getBlue() * 255));
            FireflyLuciferin.sendColorsViaUSB(leds);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

    }

    /**
     * Init all the settings listener
     * @param currentConfig stored config
     */
    public void initListeners(Configuration currentConfig) {

        // Toggle LED button listener
        toggleLed.setOnAction(e -> {
            if ((toggleLed.isSelected())) {
                toggleLed.setText(Constants.TURN_LED_OFF);
                turnOnLEDs(currentConfig, true);
                if (FireflyLuciferin.config != null) {
                    FireflyLuciferin.config.setToggleLed(true);
                }
            } else {
                toggleLed.setText(Constants.TURN_LED_ON);
                settingsController.turnOffLEDs(currentConfig);
                if (FireflyLuciferin.config != null) {
                    FireflyLuciferin.config.setToggleLed(false);
                }
            }
        });
        // Color picker listener
        EventHandler<ActionEvent> colorPickerEvent = e -> turnOnLEDs(currentConfig, true);
        colorPicker.setOnAction(colorPickerEvent);
        // Gamma can be changed on the fly
        gamma.valueProperty().addListener((ov, t, gamma) -> {
            if (currentConfig != null && currentConfig.isMqttEnable()) {
                GammaDto gammaDto = new GammaDto();
                gammaDto.setGamma(Double.parseDouble(gamma));
                MQTTManager.publishToTopic(MQTTManager.getMqttTopic(Constants.MQTT_GAMMA),
                        CommonUtility.writeValueAsString(gammaDto));
            }
            FireflyLuciferin.config.setGamma(Double.parseDouble(gamma));
        });
        // White temperature can be changed on the fly
        whiteTemperature.valueProperty().addListener((ov, t, kelvin) -> {
            FireflyLuciferin.whiteTemperature = whiteTemperature.getSelectionModel().getSelectedIndex() + 1;
            if (currentConfig != null && currentConfig.isMqttEnable()) {
                StateDto stateDto = new StateDto();
                stateDto.setState(Constants.ON);
                if (!(currentConfig.isMqttEnable() && FireflyLuciferin.RUNNING)) {
                    stateDto.setEffect(Constants.SOLID);
                }
                stateDto.setWhitetemp(FireflyLuciferin.whiteTemperature);
                MQTTManager.publishToTopic(MQTTManager.getMqttTopic(Constants.MQTT_SET), CommonUtility.writeValueAsString(stateDto));
            }
        });
        brightness.valueProperty().addListener((ov, oldVal, newVal) -> turnOnLEDs(currentConfig, false));
        audioGain.valueProperty().addListener((ov, oldVal, newVal) -> {
            DecimalFormat df = new DecimalFormat(Constants.NUMBER_FORMAT);
            float selectedGain = Float.parseFloat(df.format(newVal).replace(",","."));
            FireflyLuciferin.config.setAudioLoopbackGain(selectedGain);
        });
        effect.valueProperty().addListener((ov, oldVal, newVal) -> {
            if (FireflyLuciferin.config != null) {
                FireflyLuciferin.config.setEffect(newVal);
                setContextMenu();
                if (!oldVal.equals(newVal)) {
                    FireflyLuciferin.guiManager.stopCapturingThreads(true);
                    CommonUtility.sleepMilliseconds(100);
                    FireflyLuciferin.config.setEffect(newVal);
                    FireflyLuciferin.config.setToggleLed(true);
                    turnOnLEDs(currentConfig, true);
                }
            }
        });
        nightModeFrom.valueProperty().addListener((obs, oldValue, newValue) -> FireflyLuciferin.config.setNightModeFrom(newValue));
        nightModeTo.valueProperty().addListener((obs, oldValue, newValue) -> FireflyLuciferin.config.setNightModeTo(newValue));
        nightModeBrightness.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (FireflyLuciferin.config != null) {
                FireflyLuciferin.config.setNightModeBrightness(newValue);
            }
            enableDisableNightMode(newValue);
        });

    }

    /**
     * Turn ON LEDs
     * @param currentConfig stored config
     * @param setBrightness brightness level
     */
    void turnOnLEDs(Configuration currentConfig, boolean setBrightness) {

        if (setBrightness) {
            brightness.setValue((int)(colorPicker.getValue().getOpacity()*100));
        } else {
            colorPicker.setValue(Color.rgb((int)(colorPicker.getValue().getRed() * 255), (int)(colorPicker.getValue().getGreen() * 255),
                    (int)(colorPicker.getValue().getBlue() * 255), (brightness.getValue()/100)));
        }
        if (currentConfig != null) {
            if (toggleLed.isSelected() || !setBrightness) {
                CommonUtility.sleepMilliseconds(100);
                if (!FireflyLuciferin.RUNNING && (effect.getValue().equals(Constants.Effect.BIAS_LIGHT.getEffect())
                        || effect.getValue().equals(Constants.Effect.MUSIC_MODE_VU_METER.getEffect())
                        || effect.getValue().equals(Constants.Effect.MUSIC_MODE_BRIGHT.getEffect())
                        || effect.getValue().equals(Constants.Effect.MUSIC_MODE_RAINBOW.getEffect()))) {
                    FireflyLuciferin.guiManager.startCapturingThreads();
                } else {
                    if (currentConfig.isMqttEnable()) {
                        StateDto stateDto = new StateDto();
                        stateDto.setState(Constants.ON);
                        if (!FireflyLuciferin.RUNNING) {
                            stateDto.setEffect(effect.getValue().toLowerCase());
                        }
                        ColorDto colorDto = new ColorDto();
                        colorDto.setR((int)(colorPicker.getValue().getRed() * 255));
                        colorDto.setG((int)(colorPicker.getValue().getGreen() * 255));
                        colorDto.setB((int)(colorPicker.getValue().getBlue() * 255));
                        stateDto.setColor(colorDto);
                        stateDto.setBrightness(CommonUtility.getNightBrightness());
                        stateDto.setWhitetemp(FireflyLuciferin.config.getWhiteTemperature());
                        MQTTManager.publishToTopic(MQTTManager.getMqttTopic(Constants.MQTT_SET), CommonUtility.writeValueAsString(stateDto));
                    } else {
                        sendSerialParams();
                    }
                    FireflyLuciferin.config.setBrightness((int)((brightness.getValue() / 100) * 255));
                }
            }
        }

    }

    /**
     * Save button event
     * @param e event
     */
    @FXML
    public void save(InputEvent e) {

        settingsController.save(e);

    }

    /**
     * Set form tooltips
     * @param currentConfig stored config
     */
    void setTooltips(Configuration currentConfig) {

        gamma.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_GAMMA));
        whiteTemperature.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_WHITE_TEMP));
        if (NativeExecutor.isWindows()) {
            startWithSystem.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_START_WITH_SYSTEM));
        }
        framerate.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_FRAMERATE));
        eyeCare.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_EYE_CARE));
        brightness.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_BRIGHTNESS));
        audioDevice.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_AUDIO_DEVICE));
        audioChannels.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_AUDIO_CHANNELS));
        audioGain.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_AUDIO_GAIN));
        effect.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_EFFECT));
        colorPicker.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_COLORS));
        nightModeFrom.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_NIGHT_MODE_FROM));
        nightModeTo.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_NIGHT_MODE_TO));
        nightModeBrightness.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_NIGHT_MODE_BRIGHT));
        if (currentConfig == null) {
            saveMiscButton.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_SAVEMQTTBUTTON_NULL));
        } else {
            saveMiscButton.setTooltip(settingsController.createTooltip(Constants.TOOLTIP_SAVEMQTTBUTTON,200, 6000));
        }

    }

}
