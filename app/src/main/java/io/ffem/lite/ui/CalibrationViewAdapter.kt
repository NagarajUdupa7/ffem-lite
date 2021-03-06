package io.ffem.lite.ui

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.ffem.lite.R
import io.ffem.lite.model.CalibrationValue
import io.ffem.lite.model.TestInfo
import java.util.*

class CalibrationViewAdapter internal constructor(
    private val testInfo: TestInfo,
    private val mListener: CalibrationItemFragment.OnCalibrationSelectedListener?
) :
    RecyclerView.Adapter<CalibrationViewAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calibration, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.calibrationValue = testInfo.values[position]
        val colors: List<CalibrationValue> = testInfo.values

        if (colors[position].value >= 0) {
            val format = "%." + "2" + "f"
            holder.textValue.text = String.format(
                Locale.getDefault(), format,
                holder.calibrationValue!!.value
            )
        }

        if (position < colors.size) {
            val color = colors[position].color
            holder.mIdView.background = ColorDrawable(color)
            if (colors[position].value >= 0) {
                holder.textUnit.text = testInfo.unit.toString()
            }
        }
        holder.mView.setOnClickListener {
            mListener?.onCalibrationSelected(holder.calibrationValue, testInfo)
        }
    }

    override fun getItemCount(): Int {
        return testInfo.values.size / 2
    }

    class ViewHolder internal constructor(val mView: View) : RecyclerView.ViewHolder(mView) {
        val mIdView: Button = mView.findViewById(R.id.buttonColor)
        val textValue: TextView = mView.findViewById(R.id.textValue)
        val textUnit: TextView = mView.findViewById(R.id.textUnit)
        var calibrationValue: CalibrationValue? = null
        override fun toString(): String {
            return super.toString() + " '" + textValue.text + "'"
        }
    }
}