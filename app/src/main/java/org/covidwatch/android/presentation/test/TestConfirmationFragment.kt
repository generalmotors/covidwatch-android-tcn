package org.covidwatch.android.presentation.test

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.fragment.findNavController
import org.covidwatch.android.R
import org.covidwatch.android.databinding.FragmentTestConfirmationBinding
import org.covidwatch.android.domain.TestedRepository
import org.covidwatch.android.util.ButtonUtils
import org.koin.android.ext.android.inject

class TestConfirmationFragment : Fragment() {

    private var _binding: FragmentTestConfirmationBinding? = null
    private val binding get() = _binding!!

    private val testedRepository: TestedRepository by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTestConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.footerQuote.text = ""

        binding.toolbar.menuButton.setOnClickListener {
            findNavController().navigate(R.id.menuFragment)
        }

//        binding.continueUsingButton.setOnClickListener{
//
//        }

        val callback: OnBackPressedCallback =
            object : OnBackPressedCallback(true /* enabled by default */) {
                override fun handleOnBackPressed() {
                    goBackHome()
                }
            }

        binding.continueUsingButton.setOnTouchListener {  v, event ->

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.backgroundTintList = context?.getColor(R.color.accentYellowPressed)?.let {ColorStateList.valueOf(it)}
                    v.invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.backgroundTintList = context?.getColor(R.color.accentYellow)?.let {ColorStateList.valueOf(it)}
                    v.invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    v.backgroundTintList = context?.getColor(R.color.accentYellow)?.let {ColorStateList.valueOf(it)}
                    v.invalidate()
                    showConfirmationDialog()
                }

            }
            false

        }


//        requireActivity().onBackPressedDispatcher.addCallback(callback)




//        binding.confirmButton.setOnClickListener {
//            showConfirmationDialog()
//        }
//        binding.cancelButton.setOnClickListener {
//            findNavController().popBackStack()
//        }
//        binding.closeButton.setOnClickListener {
//            findNavController().popBackStack()
//        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun goBackHome(){
        val fmManager: FragmentManager = parentFragmentManager
        val id2 = fmManager.getBackStackEntryAt(0).getId()
        fmManager.popBackStack(id2, FragmentManager.POP_BACK_STACK_INCLUSIVE)  // go back to home
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage("Are you sure?")
            .setPositiveButton("Yes") { dialogInterface, i ->
                testedRepository.removeUserTestedPositive()
                goBackHome()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}