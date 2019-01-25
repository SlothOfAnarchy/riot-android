/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.fragments.keysbackupsetup

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.transition.TransitionManager
import android.support.v7.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import butterknife.BindView
import butterknife.OnClick
import im.vector.Matrix
import im.vector.R
import im.vector.activity.KeysBackupSetupActivity
import im.vector.activity.MXCActionBarActivity
import im.vector.activity.VectorAppCompatActivity
import im.vector.fragments.VectorBaseFragment
import im.vector.util.startSharePlainTextIntent

class KeysBackupSetupStep3Fragment : VectorBaseFragment() {

    override fun getLayoutResId() = R.layout.keys_backup_setup_step3_fragment

    @BindView(R.id.keys_backup_setup_step3_copy_button)
    lateinit var mCopyButton: Button

    @BindView(R.id.keys_backup_setup_step3_button)
    lateinit var mFinishButton: Button

    @BindView(R.id.keys_backup_recovery_key_text)
    lateinit var mRecoveryKeyTextView: TextView

    @BindView(R.id.keys_backup_recovery_key_spinner)
    lateinit var mSpinner: ProgressBar

    @BindView(R.id.keys_backup_recovery_key_spinner_text)
    lateinit var mSpinnerStatusText: TextView

    @BindView(R.id.keys_backup_setup_step3_root)
    lateinit var mRootLayout: ViewGroup

    companion object {
        fun newInstance() = KeysBackupSetupStep3Fragment()
    }

    private lateinit var viewModel: KeysBackupSetupSharedViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProviders.of(this).get(KeysBackupSetupSharedViewModel::class.java)
        } ?: throw Exception("Invalid Activity")


        viewModel.prepareRecoverFailError.observe(this, Observer { error ->
            if (error != null) {
                activity?.run {
                    AlertDialog.Builder(this)
                            .setTitle(R.string.unknown_error)
                            .setMessage(error.localizedMessage)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                //nop
                                viewModel.prepareRecoverFailError.value = null
                                activity?.onBackPressed()
                            }
                            .show()
                }
            }
        })

        val session = (activity as? MXCActionBarActivity)?.session
                ?: Matrix.getInstance(context)?.getSession(null)

        if (viewModel.recoveryKey.value == null) {
            viewModel.prepareRecoveryKey(session)
        }

        viewModel.recoveryKey.observe(this, Observer { newValue ->
            TransitionManager.beginDelayedTransition(mRootLayout)
            if (newValue == null || newValue.isEmpty()) {
                mSpinner.visibility = View.VISIBLE
                mSpinnerStatusText.visibility = View.VISIBLE
                mSpinner.animate()
                mRecoveryKeyTextView.text = null
                mRecoveryKeyTextView.visibility = View.GONE
                mCopyButton.visibility = View.GONE
                mFinishButton.visibility = View.GONE
            } else {
                mSpinner.visibility = View.GONE
                mSpinnerStatusText.visibility = View.GONE

                mRecoveryKeyTextView.text = newValue
                        .replace(" ", "")
                        .chunked(16)
                        .joinToString("\n") {
                            it
                                    .chunked(4)
                                    .joinToString(" ")
                        }

                mRecoveryKeyTextView.visibility = View.VISIBLE
                mCopyButton.visibility = View.VISIBLE
                mFinishButton.visibility = View.VISIBLE
            }
        })

        viewModel.creatingBackupError.observe(this, Observer { error ->
            if (error != null) {
                activity?.let {
                    AlertDialog.Builder(it)
                            .setTitle(R.string.unexpected_error)
                            .setMessage(error.localizedMessage)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                //nop
                                viewModel.creatingBackupError.value = null
                                activity?.onBackPressed()
                            }
                            .show()
                }
            }
        })

        viewModel.keysVersion.observe(this, Observer { keysVersion ->
            if (keysVersion != null) {
                activity?.run {
                    val resultIntent = Intent()
                    resultIntent.putExtra(KeysBackupSetupActivity.KEYS_VERSION, keysVersion.version)
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }
        })

        viewModel.isCreatingBackupVersion.observe(this, Observer { newValue ->
            val isLoading = newValue ?: false
            if (isLoading) {
                (activity as? VectorAppCompatActivity)?.showWaitingView()
            } else {
                (activity as? VectorAppCompatActivity)?.hideWaitingView()
            }
        })
    }

    @OnClick(R.id.keys_backup_setup_step3_button)
    fun onFinishButtonClicked() {
        if (viewModel.megolmBackupCreationInfo == null) {
            //nothing
        } else if (viewModel.copyHasBeenMade) {
            val session = (activity as? MXCActionBarActivity)?.session
                    ?: Matrix.getInstance(context)?.getSession(null)
            val keysBackup = session?.crypto?.keysBackup
            if (keysBackup != null) {
                viewModel.createKeysBackup(keysBackup)
            }
        } else {
            Toast.makeText(context, R.string.keys_backup_setup_step3_please_make_copy, Toast.LENGTH_LONG).show()
        }
    }

    @OnClick(R.id.keys_backup_setup_step3_copy_button)
    fun onCopyButtonClicked() {
        val recoveryKey = viewModel.recoveryKey.value
        if (recoveryKey != null) {
            startSharePlainTextIntent(this,
                    context?.getString(R.string.keys_backup_setup_step3_share_intent_chooser_title),
                    recoveryKey,
                    context?.getString(R.string.recovery_key))
            viewModel.copyHasBeenMade = true
        }
    }
}