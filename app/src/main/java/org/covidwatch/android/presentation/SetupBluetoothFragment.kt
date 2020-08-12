package org.covidwatch.android.presentation

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.res.ColorStateList
import android.os.Bundle
import android.telephony.PhoneNumberUtils
import android.text.Editable
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.covidwatch.android.CovidWatchTcnManager
import org.covidwatch.android.R
import org.covidwatch.android.domain.UserFlowRepository
import org.covidwatch.android.presentation.util.formatContactNumber
import org.covidwatch.android.presentation.util.validateContactNumber
import kotlinx.android.synthetic.main.fragment_setup_bluetooth.*
import org.koin.android.ext.android.inject
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions


private const val LOCATION_PERMISSION = 100

class SetupBluetoothFragment : Fragment(R.layout.fragment_setup_bluetooth),
    EasyPermissions.PermissionCallbacks {

    private val userFlowRepository: UserFlowRepository by inject()
    private val tcnManager: CovidWatchTcnManager by inject()

    lateinit var grantLocationAccessButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        grantLocationAccessButton = view.findViewById(R.id.grant_location_access_button)
        grantLocationAccessButton.setOnClickListener {
            grantLocationPermission()



        }
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (EasyPermissions.hasPermissions(requireContext(), *perms)) {
            permissionGranted()
        }



            if (context?.getSharedPreferences("org.covidwatch.android.PREFERENCE_FILE_KEY",MODE_PRIVATE)?.getString("contact_number_full","").toString() != "") {
            contact_phone_number2.text = Editable.Factory.getInstance().newEditable(context?.getSharedPreferences("org.covidwatch.android.PREFERENCE_FILE_KEY",MODE_PRIVATE)?.getString("contact_number_full","No number saved"))
            save_button2.text = Editable.Factory.getInstance().newEditable("Contact Number Saved!")
            save_button2.setCompoundDrawablesWithIntrinsicBounds(context?.getDrawable(R.drawable.ic_check_true),null,null,null)
            save_button2.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.maroon_shadow))
            save_button2.isClickable = false
        }
        save_button2.setOnClickListener {
            val formattedPhone = formatContactNumber(contact_phone_number2.text.toString())

            if (validateContactNumber(formattedPhone)) {

                context?.getSharedPreferences("org.covidwatch.android.PREFERENCE_FILE_KEY",MODE_PRIVATE)?.edit()?.putString("contact_number_full",formattedPhone)?.apply()
                contact_phone_number2.text = Editable.Factory.getInstance().newEditable(context?.getSharedPreferences("org.covidwatch.android.PREFERENCE_FILE_KEY",MODE_PRIVATE)?.getString("contact_number_full","No number saved"))

                save_button2.text = Editable.Factory.getInstance().newEditable("Contact Number Saved!")
                save_button2.setCompoundDrawablesWithIntrinsicBounds(context?.getDrawable(R.drawable.ic_check_true),null,null,null)
                save_button2.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.maroon_shadow))
                save_button2.isClickable = false

                if (EasyPermissions.hasPermissions(requireContext(), *perms)) {
                    setupComplete()
                }
            }
        }
        contact_phone_number2.setOnEditorActionListener { _, actionId, _ ->
            var handled = false
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_SEND) {
                val phoneNum = contact_phone_number2.text.toString()
                if (validateContactNumber(phoneNum)) {
                    context?.getSharedPreferences("org.covidwatch.android.PREFERENCE_FILE_KEY",MODE_PRIVATE)?.edit()?.putString("contact_number_full",formatContactNumber(phoneNum))?.apply()
                }
                handled = true
            }
            handled
        }
    }

    @AfterPermissionGranted(LOCATION_PERMISSION)
    private fun grantLocationPermission() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (EasyPermissions.hasPermissions(requireContext(), *perms)) {
            permissionGranted()
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(
            this,

                getString(R.string.bluetooth_explanation_subtext),
                LOCATION_PERMISSION, *perms
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        permissionGranted()
    }

    private fun permissionGranted() {
        grantLocationAccessButton.text = Editable.Factory.getInstance().newEditable("Location Access Granted!")
        grantLocationAccessButton.setCompoundDrawablesWithIntrinsicBounds(context?.getDrawable(R.drawable.ic_check_true),null,null,null)
        grantLocationAccessButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.maroon_shadow))
        grantLocationAccessButton.isClickable = false
        if (save_button2.text.toString() == "Contact Number Saved!") {
            setupComplete()
        }
    }

    private fun setupComplete() {
        if (!grantLocationAccessButton.isClickable && !save_button2.isClickable) {
            tcnManager.startService()
            userFlowRepository.updateSetupUserFlow()
            findNavController().popBackStack(R.id.homeFragment,false)
        }
    }

}