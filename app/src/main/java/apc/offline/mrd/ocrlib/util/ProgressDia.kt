package apc.offline.mrd.ocrlib.util

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import apc.offline.mrd.R


class ProgressDia : AppCompatDialogFragment() {
    private lateinit var dialog: AlertDialog
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val rootView: View = inflater.inflate(R.layout.progress_dia, null, false)
        dialog = AlertDialog.Builder(requireActivity()).setView(rootView).show()
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }
}