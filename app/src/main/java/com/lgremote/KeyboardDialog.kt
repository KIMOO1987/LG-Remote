package com.lgremote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lgremote.databinding.DialogKeyboardBinding

class KeyboardDialog(private val client: WebOSClient) : BottomSheetDialogFragment() {

    private lateinit var binding: DialogKeyboardBinding
    private var isCaps = false
    private var isNumbers = false

    private val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    private val row3 = listOf("z", "x", "c", "v", "b", "n", "m")
    
    private val numRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val numRow2 = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
    private val numRow3 = listOf("-", "_", "=", "+", "[", "]", "{", "}", "|", "\\")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogKeyboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupKeyboard()
        
        binding.btnClear.setOnClickListener {
            binding.etInput.setText("")
            client.insertText("", replace = 1)
        }
        
        binding.btnSendText.setOnClickListener {
            client.insertText(binding.etInput.text.toString(), replace = 0)
        }
        
        binding.btnReplaceAll.setOnClickListener {
            client.insertText(binding.etInput.text.toString(), replace = 1)
        }
        
        binding.btnDelete.setOnClickListener {
            val text = binding.etInput.text.toString()
            if (text.isNotEmpty()) {
                binding.etInput.setText(text.dropLast(1))
                client.deleteChar()
            }
        }
        
        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun setupKeyboard() {
        binding.keyboardGrid.removeAllViews()
        val rows = if (isNumbers) listOf(numRow1, numRow2, numRow3) else listOf(row1, row2, row3)
        
        rows.forEach { row ->
            row.forEach { char ->
                val displayChar = if (isCaps && !isNumbers) char.uppercase() else char
                addButton(displayChar)
            }
        }
        
        // Special keys
        addControlButton(if (isNumbers) "ABC" else "123") {
            isNumbers = !isNumbers
            setupKeyboard()
        }
        
        addControlButton("CAPS") {
            isCaps = !isCaps
            setupKeyboard()
        }
        
        addControlButton("SPACE") {
            binding.etInput.append(" ")
            client.insertText(" ", replace = 0)
        }
        
        addControlButton("ENTER") {
            client.sendKey("ENTER")
        }
    }

    private fun addButton(char: String) {
        val btn = Button(requireContext(), null, 0, R.style.RemoteButton_Small)
        btn.text = char
        btn.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
        btn.setOnClickListener {
            binding.etInput.append(char)
            client.insertText(char, replace = 0)
        }
        binding.keyboardGrid.addView(btn)
    }

    private fun addControlButton(label: String, action: () -> Unit) {
        val btn = Button(requireContext(), null, 0, R.style.RemoteButton_Small)
        btn.text = label
        btn.layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 2f)
        }
        btn.setOnClickListener { action() }
        binding.keyboardGrid.addView(btn)
    }
}
