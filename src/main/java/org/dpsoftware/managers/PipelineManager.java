/*
  PipelineManager.java

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
package org.dpsoftware.managers;

import lombok.extern.slf4j.Slf4j;
import org.dpsoftware.FireflyLuciferin;
import org.dpsoftware.JavaFXStarter;
import org.dpsoftware.audio.AudioLoopback;
import org.dpsoftware.audio.AudioLoopbackNative;
import org.dpsoftware.audio.AudioLoopbackSoftware;
import org.dpsoftware.audio.AudioUtility;
import org.dpsoftware.config.Configuration;
import org.dpsoftware.config.Constants;
import org.dpsoftware.gui.elements.GlowWormDevice;
import org.dpsoftware.managers.dto.StateDto;
import org.dpsoftware.managers.dto.UnsubscribeInstanceDto;
import org.dpsoftware.network.MessageClient;
import org.dpsoftware.utilities.CommonUtility;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manage high performance pipeline for screen grabbing
 */
@Slf4j
public class PipelineManager {

    private ScheduledExecutorService scheduledExecutorService;
    UpgradeManager upgradeManager = new UpgradeManager();
    public static boolean pipelineStarting = false;
    public static boolean pipelineStopping = false;
    public static String lastEffectInUse = "";

    /**
     * Start high performance pipeline, MQTT or Serial managed (FULL or LIGHT firmware)
     */
    public void startCapturePipeline() {

        PipelineManager.pipelineStarting = true;
        PipelineManager.pipelineStopping = false;
        if (CommonUtility.isSingleDeviceMainInstance() || !CommonUtility.isSingleDeviceMultiScreen()) {
            initAudioCapture();
        }
        if (MQTTManager.client != null) {
            startMqttManagedPipeline();
        } else {
            if (!FireflyLuciferin.config.isMqttEnable()) {
                startSerialManagedPipeline();
            }
        }

    }

    /**
     * Initialize audio loopback, software or native based on the OS availability
     */
    void initAudioCapture() {

        AudioUtility audioLoopback;
        audioLoopback = new AudioLoopbackNative();
        if (Constants.Effect.MUSIC_MODE_VU_METER.getEffect().equals(FireflyLuciferin.config.getEffect())
                || Constants.Effect.MUSIC_MODE_BRIGHT.getEffect().equals(FireflyLuciferin.config.getEffect())
                || Constants.Effect.MUSIC_MODE_RAINBOW.getEffect().equals(FireflyLuciferin.config.getEffect())
                || Constants.Effect.MUSIC_MODE_VU_METER.getEffect().equals(lastEffectInUse)
                || Constants.Effect.MUSIC_MODE_BRIGHT.getEffect().equals(lastEffectInUse)
                || Constants.Effect.MUSIC_MODE_RAINBOW.getEffect().equals(lastEffectInUse)) {
            Map<String, String> loopbackDevices = audioLoopback.getLoopbackDevices();
            // if there is no native audio loopback (example stereo mix), fallback to software audio loopback using WASAPI
            if (loopbackDevices != null && !loopbackDevices.isEmpty()
                    && FireflyLuciferin.config.getAudioDevice().equals(Constants.DEFAULT_AUDIO_OUTPUT)) {
                log.debug("Starting native audio loopback.");
                audioLoopback.startVolumeLevelMeter();
            } else {
                audioLoopback = new AudioLoopbackSoftware();
                loopbackDevices = audioLoopback.getLoopbackDevices();
                if (loopbackDevices != null && !loopbackDevices.isEmpty()) {
                    log.debug("Starting software audio loopback.");
                    audioLoopback.startVolumeLevelMeter();
                }
            }
        } else {
            audioLoopback.stopVolumeLevelMeter();
        }

    }

    /**
     * Start high performance Serial pipeline, LIGHT firmware required
     */
    private void startSerialManagedPipeline() {

        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        Runnable framerateTask = () -> {
            // Waiting Device to Use
            GlowWormDevice glowWormDeviceSerial = CommonUtility.getDeviceToUse();
            // Check if the connected device match the minimum firmware version requirements for this Firefly Luciferin version
            Boolean firmwareMatchMinRequirements = upgradeManager.firmwareMatchMinimumRequirements();
            if (CommonUtility.isSingleDeviceOtherInstance() || firmwareMatchMinRequirements != null) {
                if (CommonUtility.isSingleDeviceOtherInstance() || firmwareMatchMinRequirements) {
                    setRunning();
                    if (FireflyLuciferin.guiManager.getTrayIcon() != null) {
                        FireflyLuciferin.guiManager.setTrayIconImage(Constants.PlayerStatus.PLAY);
                    }
                } else {
                    stopForFirmwareUpgrade(glowWormDeviceSerial);
                }
            } else {
                log.debug("Waiting device for my instance...");
            }
        };
        scheduledExecutorService.scheduleAtFixedRate(framerateTask, 1, 1, TimeUnit.SECONDS);

    }

    /**
     * Start high performance MQTT pipeline, FULL firmware required
     */
    private void startMqttManagedPipeline() {

        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        AtomicInteger retryNumber = new AtomicInteger();
        Runnable framerateTask = () -> {
            // Waiting Device to Use
            GlowWormDevice glowWormDeviceToUse = CommonUtility.getDeviceToUse();
            // Check if the connected device match the minimum firmware version requirements for this Firefly Luciferin version
            Boolean firmwareMatchMinRequirements = (JavaFXStarter.whoAmI == 1 || !CommonUtility.isSingleDeviceMultiScreen()) ? upgradeManager.firmwareMatchMinimumRequirements() : null;
            if (CommonUtility.isSingleDeviceOtherInstance() || firmwareMatchMinRequirements != null) {
                if (CommonUtility.isSingleDeviceOtherInstance() || firmwareMatchMinRequirements) {
                    setRunning();
                    MQTTManager.publishToTopic(Constants.ASPECT_RATIO_TOPIC, FireflyLuciferin.config.getDefaultLedMatrix());
                    if (FireflyLuciferin.guiManager.getTrayIcon() != null) {
                        FireflyLuciferin.guiManager.setTrayIconImage(Constants.PlayerStatus.PLAY);
                    }
                    StateDto stateDto = new StateDto();
                    stateDto.setState(Constants.ON);
                    stateDto.setBrightness(CommonUtility.getNightBrightness());
                    stateDto.setWhitetemp(FireflyLuciferin.config.getWhiteTemperature());
                    stateDto.setMAC(glowWormDeviceToUse.getMac());
                    if ((FireflyLuciferin.config.isMqttEnable() && FireflyLuciferin.config.isMqttStream())) {
                        // If multi display change stream topic
                        if (retryNumber.getAndIncrement() < 5 && FireflyLuciferin.config.getMultiMonitor() > 1 && !CommonUtility.isSingleDeviceMultiScreen()) {
                            MQTTManager.publishToTopic(MQTTManager.getMqttTopic(Constants.MQTT_UNSUBSCRIBE),
                                    CommonUtility.toJsonString(new UnsubscribeInstanceDto(String.valueOf(JavaFXStarter.whoAmI), FireflyLuciferin.config.getSerialPort())));
                            CommonUtility.sleepSeconds(1);
                        } else {
                            retryNumber.set(0);
                            stateDto.setEffect(Constants.STATE_ON_GLOWWORMWIFI);
                            MQTTManager.publishToTopic(MQTTManager.getMqttTopic(Constants.MQTT_SET), CommonUtility.toJsonString(stateDto));
                        }
                    } else {
                        stateDto.setEffect(Constants.STATE_ON_GLOWWORM);
                        MQTTManager.publishToTopic(MQTTManager.getMqttTopic(Constants.MQTT_SET), CommonUtility.toJsonString(stateDto));
                    }
                    if (FireflyLuciferin.FPS_GW_CONSUMER > 0 || !FireflyLuciferin.RUNNING) {
                        scheduledExecutorService.shutdown();
                    }
                } else {
                    stopForFirmwareUpgrade(glowWormDeviceToUse);
                }
            } else {
                log.debug("Waiting device for my instance...");
            }
        };
        scheduledExecutorService.scheduleAtFixedRate(framerateTask, 1, 1, TimeUnit.SECONDS);

    }

    /**
     * Set running pipeline
     */
    private void setRunning() {

        FireflyLuciferin.RUNNING = true;
        FireflyLuciferin.config.setToggleLed(true);
        if (Constants.Effect.MUSIC_MODE_VU_METER.getEffect().equals(lastEffectInUse)
                || Constants.Effect.MUSIC_MODE_BRIGHT.getEffect().equals(lastEffectInUse)
                || Constants.Effect.MUSIC_MODE_RAINBOW.getEffect().equals(lastEffectInUse)) {
            FireflyLuciferin.config.setEffect(lastEffectInUse);
        } else if (!lastEffectInUse.isEmpty()) {
            FireflyLuciferin.config.setEffect(Constants.Effect.BIAS_LIGHT.getEffect());
        }

    }

    /**
     * Stop capturing pipeline, firmware on the running device is too old
     * @param glowWormDeviceToUse Glow Worm device selected in use on the current Firfly Luciferin instance
     */
    private void stopForFirmwareUpgrade(GlowWormDevice glowWormDeviceToUse) {

        PipelineManager.pipelineStarting = false;
        PipelineManager.pipelineStopping = false;
        log.error(Constants.MIN_FIRMWARE_NOT_MATCH, glowWormDeviceToUse.getDeviceName(), glowWormDeviceToUse.getDeviceVersion());
        scheduledExecutorService.shutdown();
        if (FireflyLuciferin.guiManager.getTrayIcon() != null) {
            FireflyLuciferin.guiManager.setTrayIconImage(Constants.PlayerStatus.GREY);
        }

    }

    /**
     * Stop high performance pipeline
     */
    public void stopCapturePipeline() {

        PipelineManager.pipelineStarting = false;
        PipelineManager.pipelineStopping = true;
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
        AudioLoopback audioLoopback = new AudioLoopback();
        audioLoopback.stopVolumeLevelMeter();
        if (FireflyLuciferin.guiManager.getTrayIcon() != null) {
            FireflyLuciferin.guiManager.setTrayIconImage(Constants.PlayerStatus.STOP);
            FireflyLuciferin.guiManager.popup.remove(0);
            FireflyLuciferin.guiManager.popup.insert(FireflyLuciferin.guiManager.startItem, 0);
        }
        if (FireflyLuciferin.pipe != null && ((FireflyLuciferin.config.getCaptureMethod().equals(Configuration.CaptureMethod.DDUPL.name()))
                || (FireflyLuciferin.config.getCaptureMethod().equals(Configuration.CaptureMethod.XIMAGESRC.name()))
                || (FireflyLuciferin.config.getCaptureMethod().equals(Configuration.CaptureMethod.AVFVIDEOSRC.name())))) {
            FireflyLuciferin.pipe.stop();
        }
        FireflyLuciferin.FPS_PRODUCER_COUNTER = 0;
        FireflyLuciferin.FPS_CONSUMER_COUNTER = 0;
        FireflyLuciferin.FPS_CONSUMER = 0;
        FireflyLuciferin.FPS_PRODUCER = 0;
        FireflyLuciferin.RUNNING = false;
        AudioLoopback.RUNNING_AUDIO = false;
        FireflyLuciferin.config.setToggleLed(false);
        if (FireflyLuciferin.config.getEffect().equals(Constants.Effect.MUSIC_MODE_VU_METER.getEffect())
                || FireflyLuciferin.config.getEffect().equals(Constants.Effect.MUSIC_MODE_BRIGHT.getEffect())
                || FireflyLuciferin.config.getEffect().equals(Constants.Effect.MUSIC_MODE_RAINBOW.getEffect())
                || FireflyLuciferin.config.getEffect().equals(Constants.Effect.BIAS_LIGHT.getEffect())) {
            lastEffectInUse = FireflyLuciferin.config.getEffect();
        }
        AudioLoopback.AUDIO_BRIGHTNESS = 255;
        FireflyLuciferin.config.setEffect(Constants.Effect.SOLID.getEffect());

    }

    /**
     * Calculate correct Pipeline for Linux
     * @return params for Linux Pipeline
     */
    public static String getLinuxPipelineParams() {

        // startx{0}, endx{1}, starty{2}, endy{3}
        StorageManager sm = new StorageManager();
        if (FireflyLuciferin.config.getMultiMonitor() == 2) {
            Configuration conf1 = sm.readConfig(Constants.CONFIG_FILENAME);
            Configuration conf2 = sm.readConfig(Constants.CONFIG_FILENAME_2);
            if (JavaFXStarter.whoAmI == 2) {
                return Constants.GSTREAMER_PIPELINE_LINUX
                        .replace("{0}", String.valueOf(0))
                        .replace("{1}", String.valueOf(conf2.getScreenResX() - 1))
                        .replace("{2}", String.valueOf(0))
                        .replace("{3}", String.valueOf(conf2.getScreenResY() - 1));
            } else if (JavaFXStarter.whoAmI == 1) {
                return Constants.GSTREAMER_PIPELINE_LINUX
                        .replace("{0}", String.valueOf(conf2.getScreenResX() + 1))
                        .replace("{1}", String.valueOf(conf2.getScreenResX() + conf1.getScreenResX() - 1))
                        .replace("{2}", String.valueOf(0))
                        .replace("{3}", String.valueOf(conf1.getScreenResY() - 1));
            }
        } else if (FireflyLuciferin.config.getMultiMonitor() == 3) {
            Configuration conf1 = sm.readConfig(Constants.CONFIG_FILENAME);
            Configuration conf2 = sm.readConfig(Constants.CONFIG_FILENAME_2);
            Configuration conf3 = sm.readConfig(Constants.CONFIG_FILENAME_3);
            if (JavaFXStarter.whoAmI == 3) {
                return Constants.GSTREAMER_PIPELINE_LINUX
                        .replace("{0}", String.valueOf(0))
                        .replace("{1}", String.valueOf(conf3.getScreenResX() - 1))
                        .replace("{2}", String.valueOf(0))
                        .replace("{3}", String.valueOf(conf3.getScreenResY() - 1));
            } else if (JavaFXStarter.whoAmI == 2) {
                return Constants.GSTREAMER_PIPELINE_LINUX
                        .replace("{0}", String.valueOf(conf3.getScreenResX() + 1))
                        .replace("{1}", String.valueOf(conf3.getScreenResX() + conf2.getScreenResX() - 1))
                        .replace("{2}", String.valueOf(0))
                        .replace("{3}", String.valueOf(conf2.getScreenResY() - 1));
            } else if (JavaFXStarter.whoAmI == 1) {
                return Constants.GSTREAMER_PIPELINE_LINUX
                        .replace("{0}", String.valueOf(conf3.getScreenResX() + conf2.getScreenResX() + 1))
                        .replace("{1}", String.valueOf(conf3.getScreenResX() + conf2.getScreenResX() + conf1.getScreenResX() - 1))
                        .replace("{2}", String.valueOf(0))
                        .replace("{3}", String.valueOf(conf3.getScreenResY() - 1));
            }
        }
        return Constants.GSTREAMER_PIPELINE_LINUX
                .replace("{0}", String.valueOf(0))
                .replace("{1}", String.valueOf(FireflyLuciferin.config.getScreenResX() - 1))
                .replace("{2}", String.valueOf(0))
                .replace("{3}", String.valueOf(FireflyLuciferin.config.getScreenResY() - 1));
    }

    /**
     * Message offered to the queue is sent to the LED strip, if multi screen single instance, is sent via TCP Socket to the main instance
     * @param leds colors to be sent to the LED strip
     */
    public static void offerToTheQueue(Color[] leds) {

        if (CommonUtility.isSingleDeviceMultiScreen()) {
            if (MessageClient.msgClient == null || MessageClient.msgClient.clientSocket == null) {
                MessageClient.msgClient = new MessageClient();
                if (CommonUtility.isSingleDeviceMultiScreen()) {
                    MessageClient.msgClient.startConnection(Constants.MSG_SERVER_HOST, Constants.MSG_SERVER_PORT);
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append(JavaFXStarter.whoAmI).append(",");
            for (Color color : leds) {
                sb.append(color.getRGB()).append(",");
            }
            MessageClient.msgClient.sendMessage(sb.toString());
        } else {
            FireflyLuciferin.sharedQueue.offer(leds);
        }

    }

}
