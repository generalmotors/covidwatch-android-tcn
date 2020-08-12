package org.covidwatch.android.presentation.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import org.covidwatch.android.R
import org.covidwatch.android.databinding.FragmentSettingsBinding
import org.covidwatch.android.presentation.util.formatContactNumber
import org.covidwatch.android.presentation.util.validateContactNumber
import org.covidwatch.android.util.ButtonUtils
import org.koin.androidx.viewmodel.ext.android.viewModel
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

private const val LOCATION_PERMISSION = 100
private const val REQUEST_ENABLE_BT = 101

class SettingsFragment : Fragment(), EasyPermissions.PermissionCallbacks {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingsViewModel: SettingsViewModel by viewModel()
    lateinit var prefs : SharedPreferences
    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
  
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = context?.getSharedPreferences("org.covidwatch.android.PREFERENCE_FILE_KEY",MODE_PRIVATE)!!


        settingsViewModel.hasLocationPermissionLiveData.observe(viewLifecycleOwner, Observer {
            val checkedIconId = if (it) R.drawable.ic_check_true else R.drawable.ic_info_red
            val fillColor = if (it) R.color.maroon_shadow else R.color.accentYellow
            binding.locationButton.setCompoundDrawablesWithIntrinsicBounds(checkedIconId, 0, 0, 0)
            binding.locationButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(fillColor))
            binding.locationButton.isClickable = !it
        })

        settingsViewModel.isBluetoothEnabledLiveData.observe(viewLifecycleOwner, Observer {
            val checkedIconId = if (it) R.drawable.ic_check_true else R.drawable.ic_info_red
            val fillColor = if (it) R.color.maroon_shadow else R.color.accentYellow
            binding.bluetoothButton.setCompoundDrawablesWithIntrinsicBounds(checkedIconId, 0, 0, 0)
            binding.bluetoothButton.backgroundTintList = ColorStateList.valueOf(resources.getColor(fillColor))
            binding.bluetoothButton.isClickable = !it
        })

        disableButton(binding.saveButton, prefs.getString("contact_number_full","").toString() != "")
        binding.contactPhoneNumber.text = Editable.Factory.getInstance().newEditable(getPhoneNumber())
        binding.saveButton.setOnClickListener {
            val formattedPhone = formatContactNumber(binding.contactPhoneNumber.text.toString())
            if (validateContactNumber(formattedPhone)){
                prefs.edit()?.putString("contact_number_full", formattedPhone)?.apply()
                binding.contactPhoneNumber.setText(formattedPhone)
                Toast.makeText(context,"Saved!",Toast.LENGTH_LONG).show()
            }
        }
        binding.contactPhoneNumber.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val currentNumber = prefs.getString("contact_number_full","")
                val currentField = binding.contactPhoneNumber.text.toString()
                if (validateContactNumber(formatContactNumber(currentField)) && currentField != currentNumber) {
                    enableButton(binding.saveButton)
                }else if (currentField == currentNumber) {
                    disableButton(binding.saveButton,true)
                }else {
                    disableButton(binding.saveButton,false)
                }


            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }

        })

        binding.closeButton.setOnClickListener {
            findNavController().popBackStack()
        }


        binding.locationButton.setOnTouchListener(ButtonUtils.ButtonTouchListener())
        binding.bluetoothButton.setOnTouchListener(ButtonUtils.ButtonTouchListener())


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
        settingsViewModel.onLocationPermissionResult()
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        settingsViewModel.onLocationPermissionResult()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            settingsViewModel.onBluetoothResult()
        }
    }

    @AfterPermissionGranted(LOCATION_PERMISSION)
    fun ensureLocationPermissionIsGranted() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (EasyPermissions.hasPermissions(requireContext(), *perms)) {
            return
        }
        EasyPermissions.requestPermissions(
            this,
                getString(R.string.bluetooth_explanation_subtext),
            LOCATION_PERMISSION, *perms
        )
    }

    fun ensureBluetoothIsOn() : Boolean {
        return if (bluetoothAdapter?.isEnabled == true) {
            true
        } else {
            turnOnBluetooth()
        }

    }

    private fun turnOnBluetooth() : Boolean {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent,
            REQUEST_ENABLE_BT
        )
        return true
    }


    private fun getPhoneNumber() : String {
        println("TRYING TO GET PHONE NUMBER THROUGH MAIN APP VARIABLE")
        println(prefs.toString())
        return prefs?.getString("contact_number_full","No Contact Number Stored").toString()
    }


    private fun enableButton(button : Button) : Button {
        button.isClickable = true
        button.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.accentYellow))
        button.setCompoundDrawablesWithIntrinsicBounds(0,0,0,0)

        return button
    }

    private fun disableButton(button : Button, valid : Boolean?) : Button {
        button.isClickable = false
        button.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.maroon_shadow))
        if (valid!!) {
            button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_true,0,0,0)
        }else if (!valid){
            button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_info_red,0,0,0)
        }
        return button
    }

    private fun toggleButton(button : Button) : Button {
        button.isClickable = !button.isClickable
        if (button.isClickable) {
            enableButton(button)
        }else {
            disableButton(button,null)
        }
        return button
    }



}