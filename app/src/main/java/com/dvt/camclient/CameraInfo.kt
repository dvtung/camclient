package com.dvt.camclient

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.util.Range

data class CameraInfo(
    var cameraId: String? = null, // Store the camera ID instead of CameraDevice
    var ISO: IntValue? = null,
    var Exposure: LongValue? = null,
    var EV: IntValue? = null,
    var WB: WBMode? = null,
    var Zoom: FloatValue? = null,
    var Focus: FocusInfo? = null,
    var AutoFocus: Boolean = false,
    var AutoExposure: Boolean = false,
    var initSuccessful: Boolean = false){


//    private lateinit var _iso: IntValue
//    var ISO : IntValue
//        get() = _iso
//        set(value) {
//            _iso = value
//        }
//    private lateinit var _exposure: LongValue
//    var Exposure : LongValue
//        get() = _exposure
//        set(value) {
//            _exposure = value
//        }
//    private lateinit var _ev: IntValue
//    var EV : IntValue
//        get() = _ev
//        set(value) {
//            _ev = value
//        }
//    private var _wb: WBMode? = null
//    var WB : WBMode?
//        get() = _wb
//        set(value) {
//            _wb = value
//        }
//
//    private var _initSuccessful: Boolean = false
//    var initSuccessful : Boolean
//        get() = _initSuccessful
//        set(value) {
//            _initSuccessful = value
//        }
//
//    private var _zoom : FloatValue?= null
//    var Zoom : FloatValue?
//        get() = _zoom
//        set(value){
//            _zoom = value
//        }
//
//    private var _focus : FocusInfo? = null
//    var Focus : FocusInfo?
//        get() = _focus
//        set(value){
//            _focus = value
//        }
//
//    private var _autoFocus: Boolean = false
//    var AutoFocus : Boolean
//        get() = _autoFocus
//        set(value) {
//            _autoFocus = value
//        }
//
//    private var _autoExposure: Boolean = false
//    var AutoExposure : Boolean
//        get() = _autoExposure
//        set(value) {
//            _autoExposure = value
//        }

    fun initCameraCharacteristics(cameraManager : CameraManager, cameraDevice: CameraDevice?,  cameraId: String){
        try {

            if (cameraId !in cameraManager.cameraIdList) return
            if (cameraDevice == null) return

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).build()

            //ISO
            val isoRange: Range<Int>? = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            ISO = IntValue(isoRange?.lower, isoRange?.upper, 2000)

            //Speed or Exposure
            val exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            Exposure = LongValue(exposureTimeRange?.lower, exposureTimeRange?.upper, 120000000)

            //EV
            val evRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            EV = IntValue(evRange?.lower, evRange?.upper, captureRequestBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION))

            //WB
            val wbArray = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            wbArray?.sort()
            WB = WBMode(wbArray, captureRequestBuilder.get(CaptureRequest.CONTROL_AWB_MODE))

            //Zoom
            val zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            Zoom = FloatValue(zoomRange?.lower!! * 10, zoomRange.upper!! * 10, captureRequestBuilder.get(CaptureRequest.CONTROL_ZOOM_RATIO)!! * 10)

            //Lens Focus
            val lensFocusRange = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            lensFocusRange?.sort()
            Focus = FocusInfo(lensFocusRange, captureRequestBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE))

            initSuccessful = true

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