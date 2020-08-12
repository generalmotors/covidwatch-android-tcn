package org.covidwatch.android.presentation.home

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import org.covidwatch.android.BuildConfig
import org.covidwatch.android.GlobalConstants
import org.covidwatch.android.R
import org.covidwatch.android.data.Interaction
import org.covidwatch.android.databinding.FragmentHomeBinding
import org.covidwatch.android.domain.FirstTimeUser
import org.covidwatch.android.domain.ReturnUser
import org.covidwatch.android.domain.Setup
import org.covidwatch.android.domain.TestedRepository
import org.covidwatch.android.presentation.InteractionsStateAdapter
import org.covidwatch.android.util.ButtonUtils
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.fragment_home.*
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var currentInteraction : Interaction? = null

    private val homeViewModel: HomeViewModel by viewModel()
    private val testedRepository: TestedRepository by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (testedRepository.isUserTestedPositive()){
            findNavController().navigate(R.id.testConfirmationFragment)
        }

        val interactionsStateAdapter =
            InteractionsStateAdapter(this)
        val viewPager = view.findViewById<ViewPager2>(R.id.interactions_pager)
        viewPager.adapter = interactionsStateAdapter

        val tabLayout = view.findViewById<TabLayout>(R.id.interactions_dots)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // attach
        }.attach()

        binding.swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(
                requireContext(),
                R.color.colorPrimary
            )
        )

       // println("ALL TEST_BUTTON WIDTH VALUES:${tested_button.width}\n${tested_button.layoutParams.width}\n${tested_button.measuredWidth}\n")

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (seekBar.progress > 80) {
                    seekBar.progress = 100
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (seekBar.progress > 80) {
                    seekBar.progress = 100
                    findNavController().navigate(R.id.testQuestionsFragment)
                } else {
                    seekBar.progress = 0
                }
            }

        })


        homeViewModel.onStart()
        homeViewModel.userFlow.observe(viewLifecycleOwner, Observer { userFlow ->
            when (userFlow) {
                is FirstTimeUser -> {
                    updateUiForFirstTimeUser()
                }
                is Setup -> {
                    findNavController().navigate(R.id.setupBluetoothFragment)
                }
                is ReturnUser -> {
                    updateUiForReturnUser()
                }
            }
        })
        homeViewModel.infoBannerState.observe(viewLifecycleOwner, Observer { banner ->
            when (banner) {
                is InfoBannerState.Visible -> {
                    binding.infoBanner.isVisible = true
                    binding.infoBanner.setText(banner.text)
                }
                InfoBannerState.Hidden -> {
                    binding.infoBanner.isVisible = false
                }
            }
        })

        homeViewModel.contactWarningBannerState.observe(viewLifecycleOwner, Observer { banner ->
            when (banner) {
                is ContactWarningBannerState.Visible -> {
                    binding.contactWarningBanner.isVisible = true
                    binding.contactWarningBanner.setText(banner.text)
                }
                ContactWarningBannerState.Hidden -> {
                    binding.contactWarningBanner.isVisible = false
                }
            }
        })

        homeViewModel.nearbyInteractions.observe(viewLifecycleOwner, Observer { intrcLst ->
            var currDistance: Double = 100.0
            var currDuration: String = ""

            if (intrcLst.isNotEmpty()) {
                currentInteraction = intrcLst[0]
                currDistance = intrcLst[0].distanceInFeet

                val seconds =
                    ((intrcLst[0].lastSeen.time - intrcLst[0].interactionStart.time) / 1000)
                currDuration =
                    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString()
                        .padStart(2, '0')} minute(s)"
                val interactionDistance =  intrcLst[0].interactionSpecificDistance ?: GlobalConstants.INTERACTION_MIN_DISTANCE_IN_FEET
                if (currDistance > interactionDistance) {
                    setDisplaySafe()
                    binding.warningDesc.text =
                        "Detected Model: ${intrcLst[0].detectedPhoneModel}\n Min Dist: $interactionDistance  Dist: " + String.format("%.2f",currDistance)
                    //binding.warningDesc.text = "No People - " + "%.${2}f ft. \n".format(currDistance) + currDuration // TODO: for debugging only
//            } else if (currDistance > Constants.TOO_CLOSE_DISTANCE_IN_FEET) {
//                binding.imageView3.setImageResource(R.drawable.gettingclose)
//                binding.warningHeader.setTextColor(resources.getColor(R.color.orange))
//                binding.warningHeader.setText(R.string.getting_close)
//                //  binding.warningDesc.setText(R.string.people_nearby)
//                binding.warningDesc.setText("People Near by - " + "%.${2}f ft. \n".format(currDistance) + currDuration) // TODO: for debugging only
                } else {
                    binding.imageView3.setImageResource(R.drawable.tooclose)
                    binding.warningHeader.setTextColor(resources.getColor(R.color.red_alert))
                    binding.warningHeader.setText(R.string.too_close)
                    //binding.warningDesc.setText(R.string.people_too_close)

                    binding.warningDesc.text =
                        "Detected Model: ${intrcLst[0].detectedPhoneModel}\nMin Dist: $interactionDistance  Dist: " + String.format("%.2f",currDistance) // TODO: for debugging only
                }
            } else {
                currentInteraction = null
                setDisplaySafe()
            }
        })

//        homeViewModel.userTestedPositive.observe(viewLifecycleOwner, Observer {
            updateUiForTestedPositive()
//        })

        homeViewModel.isRefreshing.observe(viewLifecycleOwner, Observer { isRefreshing ->
            binding.swipeRefreshLayout.isRefreshing = isRefreshing
        })
        initClickListeners()


    }

    private fun setDisplaySafe() {
        binding.imageView3.setImageResource(R.drawable.safe)
        binding.warningHeader.setTextColor(resources.getColor(R.color.yellowgreen)) // TODO: Sukhdev - fix depricated issue (ContextCompat.getColor(,R.color.orange))
        binding.warningHeader.setText(R.string.safe_text)
        binding.warningDesc.setText(R.string.no_people_nearby)
    }

    fun openBrowser(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    }

    private fun initClickListeners() {


        binding.toolbar.menuButton.setOnClickListener {
            findNavController().navigate(R.id.menuFragment)
        }
        binding.interactionDataButton.setOnClickListener{
            val dialogBuilder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            // set message of alert dialog
            dialogBuilder.setMessage("Are you physically at 7FT away from the detected contact for this calibration? If not, press \"Cancel\" and try again.")
                .setCancelable(false)
                .setPositiveButton("Yes") { dialog, id ->
                    run {
                        if (currentInteraction != null) {
                            homeViewModel.sendInteractionData(currentInteraction!!);
                        } else {
                            Toast.makeText(
                                this.context,
                                "Please wait for contact detection before sending data.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .setNegativeButton("Cancel") { dialog, id -> dialog.cancel()
                }
            val alert = dialogBuilder.create()
            alert.setTitle("Confirm Physical Distance")
            alert.show()
        }
        /*binding.shareAppButton.setOnClickListener {
            shareApp()
        }*/
        binding.warningBanner.setOnClickListener {
//            findNavController().navigate(R.id.potentialRiskFragment)
        }
        binding.infoBanner.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }
        binding.swipeRefreshLayout.setOnRefreshListener {
            homeViewModel.onRefreshRequested()
        }
    }

    private fun shareApp() {
        val shareText = getString(R.string.share_intent_text)
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(
            Intent.EXTRA_TEXT,
            "$shareText https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID
        )
        sendIntent.type = "text/plain"
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_text)))
    }

    private fun updateUiForFirstTimeUser() {
        seekBar.progress = 0
    }

    private fun updateUiForReturnUser() {
        seekBar.progress = 0
    }

    private fun updateUiForTestedPositive() {
        seekBar.progress = 0
        if (homeViewModel.checkIfUserTestedPositive()){
            binding.seekBar.setBackgroundResource(R.drawable.swipetrack_to_covid_free)
        } else {
            binding.seekBar.setBackgroundResource(R.drawable.swipetrack_to_positive)
        }
    }
}