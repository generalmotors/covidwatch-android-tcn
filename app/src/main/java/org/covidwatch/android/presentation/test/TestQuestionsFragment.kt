package org.covidwatch.android.presentation.test

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import org.covidwatch.android.GlobalConstants
import org.covidwatch.android.R
import org.covidwatch.android.databinding.FragmentTestQuestionsBinding
import org.covidwatch.android.domain.TestedRepository
import org.covidwatch.android.util.ButtonUtils
import org.koin.android.ext.android.inject


class TestQuestionsFragment : Fragment() {

    private var _binding: FragmentTestQuestionsBinding? = null
    private val binding get() = _binding!!

    private val testedRepository: TestedRepository by inject()

    private val testQuestionsViewModel: TestQuestionsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTestQuestionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (testedRepository.isUserTestedPositive()){
            findNavController().navigate(R.id.testConfirmationFragment)
        }

        binding.footerQuote.setText("")
        testQuestionsViewModel.testDate.observe(viewLifecycleOwner, Observer {
            val checkedIconId = if (it.isChecked) R.drawable.ic_check_true else 0
//            binding.dateButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, checkedIconId, 0)
//            binding.dateButton.text = it?.formattedDate
        })
        testQuestionsViewModel.isTested.observe(viewLifecycleOwner, Observer {
            updateUi(it)
        })
        testQuestionsViewModel.isReportButtonVisible.observe(viewLifecycleOwner, Observer {
            toggleReportButton(it)
        })

        initClickListeners()
    }

    private fun initClickListeners() {
        binding.toolbar.menuButton.setOnClickListener {
            findNavController().navigate(R.id.menuFragment)
        }

        binding.dialCtButton.setOnClickListener{
            val phone = GlobalConstants.MEDICAL_NUMBER
            val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
            startActivity(intent)
        }
        binding.dialCtButton.setOnTouchListener(ButtonUtils.ButtonTouchListener())

        binding.reportButton.setOnTouchListener {v, event ->
            when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.backgroundTintList = context?.getColor(R.color.red_alert_pressed)?.let {ColorStateList.valueOf(it)}
                    v.invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    v.backgroundTintList = context?.getColor(R.color.red_alert)?.let {ColorStateList.valueOf(it)}
                    v.invalidate()
                    showConfirmationDialog()
                }
        }

            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage("Are you sure?")
            .setPositiveButton("Yes") { dialogInterface, i ->
                testedRepository.setUserTestedPositive()
                findNavController().navigate(R.id.testConfirmationFragment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUi(isTested: Boolean) {
        val negativeIconId = if (isTested) 0 else R.drawable.ic_check_true
        val positiveIconId = if (isTested) R.drawable.ic_check_true else 0
    }

    private fun toggleReportButton(isVisible: Boolean) {
//        binding.reportButton.isVisible = isVisible
//        binding.reportButtonText.isVisible = isVisible
    }
}
