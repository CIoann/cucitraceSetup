#define _CRT_SECURE_NO_DEPRECATE
#include <ctime>
#include <exception>
#include <tobii_research.h>
#include <tobii_research_calibration.h>
#include <tobii_research_eyetracker.h>
#include <tobii_research_streams.h>
#include <iostream>

/**include <tobii/sdk/cpp/MainLoop.hpp>
#include <tobii/sdk/cpp/EyeTracker.hpp>
#include <tobii/sdk/cpp/EyeTrackerInfo.hpp>
#include <tobii/sdk/cpp/EyeTrackerException.hpp>
#include <tobii/sdk/cpp/EyeTrackerBrowser.hpp>
#include <tobii/sdk/cpp/EyeTrackerBrowserFactory.hpp>
#include <tobii/sdk/cpp/EyeTrackerFactory.hpp>
#include <tobii/sdk/cpp/GazeDataItem.hpp>
**/
#include "edu_ysu_itrace_trackers_TobiiTracker.h"
#include "edu_ysu_itrace_trackers_TobiiTracker_BackgroundThread.h"
#include "edu_ysu_itrace_trackers_TobiiTracker_Calibrator.h"

//using namespace tobii::sdk::cpp;

struct TobiiNativeData
{
	JavaVM* jvm;
	jobject j_tobii_tracker;
	jobject j_background_thread;
	TobiiResearchEyeTracker* eye_tracker;
	TobiiResearchCalibrationData* calibration_data;// = NULL;
	TobiiResearchCalibrationResult* calibration_result;// = NULL;
	//MainLoop main_loop;
   

};



//Only one TobiiTracker can be active at one time.
bool g_already_initialised = false;
//Sort of ugly but necessary. When connecting to eye tracker, this is used to
//pass the tracker information to the main thread.
//EyeTrackerInfo::pointer_t g_et_info = EyeTrackerInfo::pointer_t();
//Also not very clean.
TobiiNativeData* g_native_data_current = NULL;

void throwJException(JNIEnv* env, const char* jclass_name, const char* msg)
{
	jclass jclass = env->FindClass(jclass_name);
	env->ThrowNew(jclass, msg);
	env->DeleteLocalRef(jclass);
}

jfieldID getFieldID(JNIEnv* env, jobject obj, const char* name, const char* sig)
{
	jclass jclass = env->GetObjectClass(obj);
	if (jclass == NULL)
		return NULL;
	jfieldID jfid = env->GetFieldID(jclass, name, sig);
	if (jfid == NULL)
		return NULL;
	return jfid;
}

TobiiNativeData* getTobiiNativeData(JNIEnv* env, jobject obj)
{
	jfieldID jfid_native_data = getFieldID(env, obj, "native_data",
		"Ljava/nio/ByteBuffer;");

	if (jfid_native_data == NULL) 
	{
		return NULL;
	}

	jobject native_data_bb = env->GetObjectField(obj, jfid_native_data);

	return (TobiiNativeData*) env->GetDirectBufferAddress(native_data_bb);
}

JNIEXPORT jboolean JNICALL
	Java_edu_ysu_itrace_trackers_TobiiTracker_00024BackgroundThread_jniBeginTobiiMainloop
	(JNIEnv* env, jobject obj)
{

	//Initialise Tobii SDK if not yet initialised, else an error condition has
	//occurred.
	if (g_already_initialised)
		return JNI_FALSE;
	else
	{
		//tobii::sdk::cpp::Library::init();
		g_already_initialised = true;
	}
	//Get native data ByteBuffer field in TobiiTracker object.
	jfieldID jfid_parent = getFieldID(env, obj, "parent",
		"Ledu/ysu/itrace/trackers/TobiiTracker;");
	if (jfid_parent == NULL)
		return JNI_FALSE;
	jobject parent_tobii_tracker = env->GetObjectField(obj, jfid_parent);
	jfieldID jfid_native_data = getFieldID(env, parent_tobii_tracker,
		"native_data", "Ljava/nio/ByteBuffer;");
	if (jfid_native_data == NULL)
		return JNI_FALSE;
	//Create structure to hold instance-specific data.

	TobiiNativeData* native_data = new TobiiNativeData();


	jobject native_data_bb = env->NewDirectByteBuffer((void*) native_data,
		sizeof(TobiiNativeData));
	//Set java virtual machine and BackgroundThread reference.
	env->GetJavaVM(&native_data->jvm);
	native_data->j_background_thread = env->NewGlobalRef(obj);
	//Store structure reference in Java object.
	env->SetObjectField(parent_tobii_tracker, jfid_native_data, native_data_bb);


	//Run!
	//native_data->main_loop.run();

	//This code does not execute until the main loop has been stopped.
	//delete native_data;

	//return JNI_TRUE;
}

/**void handleBrowserEvent(EyeTrackerBrowser::event_type_t type,
	EyeTrackerInfo::pointer_t et_info)
{
	if (type == EyeTrackerBrowser::TRACKER_FOUND)
		g_et_info = et_info;
}**/
int apply_licenses_example(TobiiResearchEyeTracker* eyetracker, const char* license_file_path) {
#define NUM_OF_LICENSES 1
	char* license_key_ring[NUM_OF_LICENSES];
	FILE *license_file = fopen(license_file_path, "rb");
	if (!license_file) {
		printf("License not found!\n");
		return 0;
	}
	fseek(license_file, 0, SEEK_END);
	size_t file_size = (size_t)ftell(license_file);
	rewind(license_file);
	if (file_size <= 0) {
		printf("License is empty!\n");
		return 0;
	}
	license_key_ring[0] = (char*)malloc(file_size);
	if (license_key_ring[0]) {
		fread(license_key_ring[0], sizeof(char), file_size, license_file);
	}
	fclose(license_file);
	printf("Applying license from %s.\n", license_file_path);
	fflush(stdout);
	TobiiResearchLicenseValidationResult validation_results;
	TobiiResearchStatus retval = tobii_research_apply_licenses(eyetracker, (const void**)license_key_ring, &file_size, &validation_results, NUM_OF_LICENSES);
	free(license_key_ring[0]);
	if (retval == TOBII_RESEARCH_STATUS_OK && validation_results == TOBII_RESEARCH_LICENSE_VALIDATION_RESULT_OK) {
		printf("Successfully applied license from list of keys.\n");
		fflush(stdout);
		return 1;
	}
	return 0;
}
JNIEXPORT jboolean JNICALL
Java_edu_ysu_itrace_trackers_TobiiTracker_jniConnectTobiiTracker(
	JNIEnv* env, jobject obj, jint timeout_seconds)
{
	//Get native data from object.

	TobiiNativeData* native_data = getTobiiNativeData(env, obj);

	if (native_data == NULL)
	{
		return JNI_FALSE;
	}
	//Set TobiiTracker reference.

	native_data->j_tobii_tracker = env->NewGlobalRef(obj);
	//Find Tobii trackers.
	/**EyeTrackerBrowser::pointer_t browser =
		EyeTrackerBrowserFactory::createBrowser(native_data->main_loop);**/
	/**browser->start();
	browser->addEventListener(handleBrowserEvent);**/
	time_t start_time = time(NULL);
	//Wait until found or timeout occurs.
	//Currently does not have this 
	while (0)//g_et_info == NULL)
	{
		if (time(NULL) > start_time + timeout_seconds)
		{
			//browser->stop();
			return JNI_FALSE;
		}
	}
	//EyeTrackerInfo::pointer_t et_info = g_et_info;
	//browser->stop();

	//Connect eye tracker
	/**cker = et_info->getEyeTrackerFactory()->
		createEyeTracker(native_data->main_loop); **/
	TobiiResearchEyeTrackers* eyetrackers = NULL;
    TobiiResearchStatus result = tobii_research_find_all_eyetrackers(&eyetrackers);
	native_data->eye_tracker = eyetrackers->eyetrackers[0];
	//Setting license
	int license_success;
	//HARD CODED. BAD
	license_success = apply_licenses_example(native_data->eye_tracker, "C:/Users/coio/Documents/cucitraceSetup/Tobii - Installation Guide/TobiiDrivers/tobii_lisence");
	//Needs a check for whether eyetracker is found.
	

   // int64_t system_time_stamp;
   // TobiiResearchStatus status = tobii_research_get_system_time_stamp(&system_time_stamp);                       
	 
	return JNI_TRUE;
}


JNIEXPORT void JNICALL Java_edu_ysu_itrace_trackers_TobiiTracker_close
	(JNIEnv* env, jobject obj)
{
	//Get native data from object.
	TobiiNativeData* native_data = getTobiiNativeData(env, obj);
	                       
	 
	if (native_data == NULL)
	{
		throwJException(env, "java/lang/RuntimeException",
			"Cannot find native data.");
		return;
	}

	//Shut down main loop
	//native_data->main_loop.quit();
}

void handleGazeData(TobiiResearchGazeData* gaze_data)
{
	JNIEnv* env = NULL;
	g_native_data_current->jvm->AttachCurrentThread((void**) &env, NULL);
	jint rs = g_native_data_current->jvm->GetEnv((void**) &env, JNI_VERSION_1_6);
	if (rs != JNI_OK || env == NULL) {
		return;
	}
	jobject obj = g_native_data_current->j_tobii_tracker;

	jclass tobii_tracker_class = env->GetObjectClass(obj);
	if (tobii_tracker_class == NULL)
		return;
	jmethodID jmid_new_gaze_point = env->GetMethodID(tobii_tracker_class,
		"newGazePoint", "(JDDDDIIDD)V");
	//Just pretend nothing happened.
	if (jmid_new_gaze_point == NULL)
		return;
	//Call newGazePoint.
	env->CallVoidMethod(obj, jmid_new_gaze_point, (jlong)gaze_data->device_time_stamp,
		gaze_data->left_eye.gaze_point.position_on_display_area.x, gaze_data->right_eye.gaze_point.position_on_display_area.y,
		gaze_data->right_eye.gaze_point.position_on_display_area.x, gaze_data->right_eye.gaze_point.position_on_display_area.y,
		gaze_data->left_eye.pupil_data.validity, gaze_data->right_eye.pupil_data.validity,
		gaze_data->left_eye.pupil_data.diameter, gaze_data->right_eye.pupil_data.diameter);
	
	/**env->CallVoidMethod(obj, jmid_new_gaze_point, (jlong) gaze_data->time_stamp,
		gaze_data->leftGazePoint2d.x, gaze_data->rightGazePoint2d.y,
		gaze_data->rightGazePoint2d.x, gaze_data->rightGazePoint2d.y,
		gaze_data->leftValidity, gaze_data->rightValidity,
		gaze_data->leftPupilDiameter, gaze_data->rightPupilDiameter);**/
}

void gaze_data_callback(TobiiResearchGazeData* gaze_data, void* user_data) {
	handleGazeData(gaze_data);
	//memcpy(user_data, gaze_data, sizeof(*gaze_data));
}


JNIEXPORT void JNICALL Java_edu_ysu_itrace_trackers_TobiiTracker_startTracking
	(JNIEnv* env, jobject obj)
{

	//Do not continue if already tracking
	if (g_native_data_current != NULL)
	{
		throwJException(env, "java/io/IOException", "Already tracking.");
		return;
	}


	//Get native data from object.
	TobiiNativeData* native_data = getTobiiNativeData(env, obj);
	if (native_data == NULL)
	{
		throwJException(env, "java/lang/RuntimeException",
			"Cannot find native data.");
		return;
	}
	//Set native data for current tracking TobiiTracker.
	g_native_data_current = native_data;
	//Setting gaze data
	TobiiResearchGazeData gaze_data;
	try
	{
		tobii_research_subscribe_to_gaze_data(native_data->eye_tracker, gaze_data_callback, &gaze_data);
		//handleGazeData(&gaze_data);
		//native_data->eye_tracker->startTracking();
		//native_data->eye_tracker->addGazeDataReceivedListener(handleGazeData);
	}
	catch (const std::invalid_argument& e)
	{
		throwJException(env, "java/io/IOException", e.what());
		return;
	}
}


JNIEXPORT void JNICALL Java_edu_ysu_itrace_trackers_TobiiTracker_stopTracking
	(JNIEnv* env, jobject obj)
{

	try
	{
		tobii_research_unsubscribe_from_gaze_data(g_native_data_current->eye_tracker, gaze_data_callback);
		g_native_data_current = NULL;
	}
	catch (const std::invalid_argument& e)
	{
		throwJException(env, "java/io/IOException", e.what());
		return;
	}
}

JNIEXPORT void
	JNICALL Java_edu_ysu_itrace_trackers_TobiiTracker_00024Calibrator_jniAddPoint
	(JNIEnv* env, jobject obj, jdouble x, jdouble y)
{

	//Get native data from parent TobiiTracker
	jfieldID jfid_parent = getFieldID(env, obj, "parent",
		"Ledu/ysu/itrace/trackers/TobiiTracker;");
	if (jfid_parent == NULL)
	{
		throwJException(env, "java/lang/RuntimeException",
			"Parent TobiiTracker not found.");
		return;
	}
	jobject parent_tobii_tracker = env->GetObjectField(obj, jfid_parent);
	TobiiNativeData* native_data = getTobiiNativeData(env, parent_tobii_tracker);

	try
	{
		TobiiResearchNormalizedPoint2D point[1] = {x,y};
		//Add new calibration point
		tobii_research_screen_based_calibration_collect_data(native_data->eye_tracker, point->x, point->y);

/**		if (tobii_research_screen_based_calibration_collect_data(native_data->eye_tracker, point->x, point->y) != TOBII_RESEARCH_STATUS_OK) {
			printf("addPoint went bad");
			fflush(stdout);
			/* Try again if it didn't go well the first time. */
			/* Not all eye tracker models will fail at this point, but instead fail on ComputeAndApply. */
			//tobii_research_screen_based_calibration_collect_data(native_data->eye_tracker, point->x, point->y);
		//}
		
		
		//native_data->eye_tracker->addCalibrationPoint(Point2d(
		//	(double) x, (double) y));
	}
	catch (const std::invalid_argument& e)
	{
		throwJException(env, "java/io/IOException", e.what());
		return;
	} 
}

JNIEXPORT void JNICALL
	Java_edu_ysu_itrace_trackers_TobiiTracker_00024Calibrator_jniStartCalibration
	(JNIEnv* env, jobject obj)
{

	//Get native data from parent TobiiTracker
	jfieldID jfid_parent = getFieldID(env, obj, "parent",
		"Ledu/ysu/itrace/trackers/TobiiTracker;");
	if (jfid_parent == NULL)
	{
		throwJException(env, "java/lang/RuntimeException",
			"Parent TobiiTracker not found.");
		return;
	}
	jobject parent_tobii_tracker = env->GetObjectField(obj, jfid_parent);
	TobiiNativeData* native_data = getTobiiNativeData(env, parent_tobii_tracker);
	try
	{
		//Start and clear 
		tobii_research_screen_based_calibration_enter_calibration_mode(native_data->eye_tracker);
		//native_data->eye_tracker->startCalibration();
		//native_data->eye_tracker->clearCalibration();
	}
	catch (const std::invalid_argument& e)
	{
		throwJException(env, "java/io/IOException", e.what());
		return;
	}
}

JNIEXPORT void JNICALL
	Java_edu_ysu_itrace_trackers_TobiiTracker_00024Calibrator_jniStopCalibration
	(JNIEnv* env, jobject obj)
{

	//Get native data from parent TobiiTracker
	jfieldID jfid_parent = getFieldID(env, obj, "parent",
		"Ledu/ysu/itrace/trackers/TobiiTracker;");
	if (jfid_parent == NULL)
	{
		throwJException(env, "java/lang/RuntimeException",
			"Parent TobiiTracker not found.");
		return;
	}
	jobject parent_tobii_tracker = env->GetObjectField(obj, jfid_parent);
	TobiiNativeData* native_data = getTobiiNativeData(env, parent_tobii_tracker);

	try
	{
		//Compute and stop calibration


		TobiiResearchStatus status = tobii_research_screen_based_calibration_compute_and_apply(native_data->eye_tracker, &native_data->calibration_result);
		tobii_research_screen_based_calibration_leave_calibration_mode(native_data->eye_tracker);
		//old
		//native_data->eye_tracker->computeCalibration();
		//native_data->eye_tracker->stopCalibration();
	}
	catch (const std::invalid_argument& e)
	{
		throwJException(env, "java/io/IOException", e.what());
		return;
	}
}
	
JNIEXPORT jdoubleArray JNICALL
	Java_edu_ysu_itrace_trackers_TobiiTracker_00024Calibrator_jniGetCalibration
  	(JNIEnv *env, jobject obj)
{
	//Get native data from parent TobiiTracker
	jfieldID jfid_parent = getFieldID(env, obj, "parent",
		"Ledu/ysu/itrace/trackers/TobiiTracker;");
	if (jfid_parent == NULL)
	{
		throwJException(env, "java/lang/RuntimeException",
			"Parent TobiiTracker not found.");
		return NULL;
	}
	jobject parent_tobii_tracker = env->GetObjectField(obj, jfid_parent);
	TobiiNativeData* native_data = getTobiiNativeData(env, parent_tobii_tracker);

	try
	{
		//Get calibration
		//Calibration::pointer_t calibrationData =
		//		native_data->eye_tracker->getCalibration();
		//		Calibration::plot_data_vector_t calibrationPlotData = calibrationData->getPlotData();
				//TobiiResearchCalibrationResult* calibration_result = NULL;
				//status = tobii_research_screen_based_calibration_compute_and_apply(native_data->eye_tracker, &calibration_result);

				//int itemCount = static_cast<int>(calibration_result->calibration_point_count);

		int itemCount = static_cast<int>(native_data->calibration_result->calibration_point_count);
		jdoubleArray calibrationPoints = env->NewDoubleArray(4 * itemCount);  // allocate

		if (NULL == calibrationPoints) return NULL;

		jdouble *points = env->GetDoubleArrayElements(calibrationPoints, 0);

		TobiiResearchCalibrationPoint item;
		//tobii_research_apply_calibration_data(native_data->eye_tracker, calibration_data);

		//Writing data. for test

		for(int i = 0; i < itemCount; i++)
		{

			item = native_data->calibration_result->calibration_points[i];
			points[i] = item.calibration_samples->left_eye.position_on_display_area.x;
			points[itemCount + i] = item.calibration_samples->left_eye.position_on_display_area.y;
			points[2 * itemCount + i] = item.calibration_samples->right_eye.position_on_display_area.x;
			points[3*itemCount+i] = item.calibration_samples->right_eye.position_on_display_area.y;

			//points[i] = item.leftMapPosition.x;
			//points[itemCount+i] = item.leftMapPosition.y;
			//points[2*itemCount+i] = item.rightMapPosition.x;
			//points[3*itemCount+i] = item.rightMapPosition.y;
		}

		env->ReleaseDoubleArrayElements(calibrationPoints, points, 0);
		return calibrationPoints;
	}
	catch (const std::invalid_argument& e)
	{
		throwJException(env, "java/io/IOException", e.what());
		return NULL;
	} 
}