package com.udacity.catpoint.security.services;

import com.udacity.catpoint.image.services.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Service that receives information about changes to the security system.
 * Responsible for
 * forwarding updates to the repository and making any decisions about changing
 * the system state.
 * This is the class that should contain most of the business logic for our
 * system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private final ImageService imageService;
    private final SecurityRepository securityRepository;
    private final Set<StatusListener> statusListeners = new HashSet<>();

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     * 
     * @param armingStatus The new arming status.
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            ConcurrentSkipListSet<Sensor> sensors = new ConcurrentSkipListSet<>(getSensors());
            sensors.forEach(sensor -> changeSensorActivationStatus(sensor, false));
        }
        statusListeners.forEach(StatusListener::sensorStatusChanged);
        securityRepository.setArmingStatus(armingStatus);
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * 
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (!cat && allSensorsDeactivated()){
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    private boolean allSensorsDeactivated() {
        Set<Sensor> sensors = getSensors();
        for (Sensor sensor: sensors) {
            if (sensor.getActive()) return false;
        }
        return true;
    }

    /**
     * Register the StatusListener for alarm system updates from within the
     * SecurityService.
     * 
     * @param statusListener The listener to add.
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     * 
     * @param status The new alarm status.
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    public void changeSensorActivationStatus(Sensor sensor) {
        AlarmStatus actualAlarmStatus = this.getAlarmStatus();
        ArmingStatus actualArmingStatus = this.getArmingStatus();

        if (actualAlarmStatus == AlarmStatus.PENDING_ALARM && !sensor.getActive()) {
            handleSensorDeactivated();
        } else if (actualAlarmStatus == AlarmStatus.ALARM && actualArmingStatus == ArmingStatus.DISARMED) {
            handleSensorDeactivated();
        }
        securityRepository.updateSensor(sensor);
    }

    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        AlarmStatus actualAlarmStatus = securityRepository.getAlarmStatus();

        if (actualAlarmStatus != AlarmStatus.ALARM) {
            if (active) {
                handleSensorActivated();
            } else if (sensor.getActive()) {
                handleSensorDeactivated();
            }
        }
        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
    }

    /**
     * Internal method for updating the alarm status when a sensor has been
     * activated.
     */
    private void handleSensorActivated() {
        if (securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; // no problem if the system is disarmed
        }
        switch (securityRepository.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been
     * deactivated
     */
    private void handleSensorDeactivated() {
        switch (securityRepository.getAlarmStatus()) {
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.NO_ALARM);
            case ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will
     * use it's provided
     * ImageService to analyze the image for cats and update the alarm status
     * accordingly.
     * 
     * @param currentCameraImage The image to process.
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
