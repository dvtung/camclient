package com.dvt.camclient

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Range

class CameraInfo(val cameraManager : CameraManager, val cameraDevice: CameraDevice?,  var cameraId: String)  {

    private lateinit var _iso: IntValue
    var ISO : IntValue
        get() = _iso
        set(value) {
            _iso = value
        }
    private lateinit var _exposure: LongValue
    var Exposure : LongValue
        get() = _exposure
        set(value) {
            _exposure = value
        }
    private lateinit var _ev: IntValue
    var EV : IntValue
        get() = _ev
        set(value) {
            _ev = value
        }
    private var _wb: WBMode? = null
    var WB : WBMode?
        get() = _wb
        set(value) {
            _wb = value
        }

    private var _initSuccessful: Boolean = false
    var initSuccessful : Boolean
        get() = _initSuccessful
        set(value) {
            _initSuccessful = value
        }

    private var _zoom : FloatValue?= null
    var Zoom : FloatValue?
        get() = _zoom
        set(value){
            _zoom = value
        }

    private var _focus : FocusInfo? = null
    var Focus : FocusInfo?
        get() = _focus
        set(value){
            _focus = value
        }

    private var _autoFocus: Boolean = true
    var AutoFocus : Boolean
        get() = _autoFocus
        set(value) {
            _autoFocus = value
        }

    fun initCameraCharacteristics(){
        try {

            if (cameraId !in cameraManager.cameraIdList) return
            if (cameraDevice == null) return

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).build()

            //ISO
            val isoRange: Range<Int>? = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            _iso = IntValue(isoRange?.lower, isoRange?.upper, captureRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY))

            //Speed or Exposure
            val exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            _exposure = LongValue(exposureTimeRange?.lower, exposureTimeRange?.upper, captureRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME))

            //EV
            val evRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            _ev = IntValue(evRange?.lower, evRange?.upper, captureRequestBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION))

            //WB
            val wbArray = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            wbArray?.sort()
            _wb = WBMode(wbArray, captureRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE))

            //Zoom
            val zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            _zoom = FloatValue(zoomRange?.lower!! * 10, zoomRange.upper!! * 10, captureRequestBuilder.get(CaptureRequest.CONTROL_ZOOM_RATIO)!! * 10)

            //Lens Focus
            val lensFocusRange = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            lensFocusRange?.sort()
            _focus = FocusInfo(lensFocusRange, captureRequestBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE))

            _initSuccessful = true

        } catch (ex: Exception){
            ex.printStackTrace()
        }
    }

}

data class IntValue(var min: Int?, var max : Int?, var value: Int?)
data class LongValue(var min: Long?, var max: Long?, var value: Long?)
data class WBMode(var modes: IntArray?, var value: Int?)
data class FocusInfo(var focusArray: FloatArray?, var value: Float?)
data class FloatValue(var min: Float?, var max: Float?, var value: Float?)

enum class Funcs{
    ISO,
    EXP,
    EV,
    WB,
    ZOOM,
    NONE
}