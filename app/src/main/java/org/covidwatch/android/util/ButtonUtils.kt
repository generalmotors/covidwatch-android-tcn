package org.covidwatch.android.util

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.Toast
import org.covidwatch.android.GlobalConstants
import org.covidwatch.android.R
import org.covidwatch.android.presentation.settings.SettingsFragment


class ButtonUtils {

    // TODO: josh - accessibility adjustments for performClickOverride

//    class CustomButtonView : AppCompatButton {
//        constructor(context: Context?) : super(context) {}
//        constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
//
//        override fun onTouchEvent(event : MotionEvent) : Boolean {
//            super.onTouchEvent(event)
//            when (event.action) {
//                MotionEvent.ACTION_DOWN -> {
//                    Toast.makeText(context,"USING NEW OVERRIDE",Toast.LENGTH_LONG).show()
//                    invalidate()
//                }
//                MotionEvent.ACTION_UP -> {
//                    performClick()
//                    invalidate()
//                    return true
//                }
//            }
//            return false
//        }
//
//        override fun performClick(): Boolean {
//            super.performClick()
//            return true
//        }
//
//    }


    class ButtonTouchListener: OnTouchListener {


        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (v.isClickable) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.backgroundTintList =
                            ColorStateList.valueOf(v.context.getColor(R.color.accentYellowPressed))
                        v.invalidate()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.backgroundTintList =
                            ColorStateList.valueOf(v.context.getColor(R.color.accentYellow))
                        v.invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        when (v.id) {

                            2131361878 -> { // Turn On Bluetooth
                                SettingsFragment()
                                    .ensureBluetoothIsOn()
                                v.backgroundTintList = ColorStateList.valueOf(v.context.getColor(R.color.maroon_shadow))
                                v.invalidate()
                            }
                            2131361983 -> { // Grant Location Access
                                SettingsFragment()
                                    .ensureLocationPermissionIsGranted()
                                v.backgroundTintList = ColorStateList.valueOf(v.context.getColor(R.color.maroon_shadow))
                                v.invalidate()
                            }
                            2131362067 -> { // Save Contact Button
                                Toast.makeText(v.context,"Saving Number...",Toast.LENGTH_LONG).show()
//                                SettingsFragment().savePhoneNumber()
                                Toast.makeText(v.context,"Number Saved!",Toast.LENGTH_LONG).show()
                                v.backgroundTintList = ColorStateList.valueOf(v.context.getColor(R.color.maroon_shadow))
                                v.invalidate()

                            }
                            2131361921 -> { // Dial GM Medical
                                v.backgroundTintList = ColorStateList.valueOf(v.context.getColor(R.color.accentYellow))
                                v.invalidate()
                                val phone = GlobalConstants.MEDICAL_NUMBER
                                val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone.toString(), null))
                                v.context.startActivity(intent)

                            }
                        }
                    }
                }
            }
            return false
        }
    }
}