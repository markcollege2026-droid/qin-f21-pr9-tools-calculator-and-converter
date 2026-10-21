package com.qin.f21.toolbox.ui

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.data.HistoryEntry
import com.qin.f21.toolbox.core.wallet.Money
import com.qin.f21.toolbox.core.wallet.WalletAccount
import com.qin.f21.toolbox.core.wallet.WalletChange
import com.qin.f21.toolbox.core.wallet.WalletEntry
import com.qin.f21.toolbox.core.wallet.WalletEntryKind
import com.qin.f21.toolbox.core.wallet.WalletLedger
import com.qin.f21.toolbox.core.wallet.WalletErrorCode

/**
 * Records one movement: an expense, a top up or a withdrawal from the main balance into a
 * cash pocket.
 *
 * The amount is always typed in the currency of the balance it lands in - for a withdrawal
 * that is the currency of the cash pocket - and the screen shows what the movement costs
 * the debited balance, converted with the bundled offline rates. Nothing is written until
 * the movement is saved, and the ledger itself refuses a movement the balance cannot cover.
 */
class WalletEntryScreen(
    host: ScreenHost,
    private val kind: WalletEntryKind
) : Screen(host) {

    override val title: String
        get() = when (kind) {
            WalletEntryKind.TOP_UP -> host.activity.getString(R.string.wallet_top_up)
            WalletEntryKind.EXPENSE -> host.activity.getString(R.string.wallet_expense)
            WalletEntryKind.WITHDRAW -> host.activity.getString(R.string.wallet_withdraw)
        }

    override val icon: String? = "ic_wallet"

    private var candidates: List<WalletAccount> = emptyList()
    private var selectedId: String = ""
    private var amount = ""

    private lateinit var column: LinearLayout
    private lateinit var accountSpinner: Spinner
    private lateinit var amountField: EditText
    private lateinit var currencyLabel: TextView
    private lateinit var noteField: EditText
    private lateinit var infoLabel: TextView
    private lateinit var errorLabel: TextView
    private var updating = false

    /** False when there is no account to book the movement on, so no form was built. */
    private var ready = false

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        Toolbox.wallet.ensureMainAccount(
            context.getString(R.string.wallet_main_name),
            Toolbox.currency.currencies,
            Toolbox.currency.base
        )
        val ledger = Toolbox.wallet.ledger

        candidates = when (kind) {
            // a withdrawal always leaves the main balance, so only the pockets are listed
            WalletEntryKind.WITHDRAW -> ledger.cashAccounts
            else -> ledger.accounts
        }

        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = ScrollView(context).forKeypad()
        column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 6)
        )
        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        if (candidates.isEmpty()) {
            ready = false
            val note = Widgets.caption(context, context.getString(R.string.wallet_no_cash))
            column.addView(note)
            val create = Widgets.button(context, context.getString(R.string.wallet_add_account), secondary = true)
            create.setOnClickListener { host.push(WalletAccountScreen(host, null)) }
            column.addView(create, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Widgets.dp(context, 8) })
            return root
        }
        ready = true
        selectedId = candidates.first().id

        // ---- account picker ------------------------------------------------------
        column.addView(Widgets.caption(context, context.getString(accountLabel())))
        accountSpinner = Spinner(context)
        accountSpinner.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline),
            6
        )
        accountSpinner.adapter = ArrayAdapter(
            context, R.layout.item_spinner, R.id.text1, candidates.map { optionLabel(it) }
        )
        accountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                candidates.getOrNull(position)?.let {
                    selectedId = it.id
                    update()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        column.addView(accountSpinner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // ---- amount --------------------------------------------------------------
        val amountRow = Widgets.row(context)
        amountRow.gravity = Gravity.CENTER_VERTICAL
        amountField = Widgets.numericField(context, "0.00")
        amountField.gravity = Gravity.END
        amountField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                amount = s?.toString() ?: ""
                update()
            }
        })
        amountRow.addView(amountField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        currencyLabel = Widgets.text(context, "", R.style.Text_Subtitle)
        currencyLabel.setTextColor(Widgets.color(context, R.color.text_secondary))
        amountRow.addView(currencyLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = Widgets.dp(context, 8) })
        val amountParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        amountParams.topMargin = Widgets.dp(context, 8)
        column.addView(amountRow, amountParams)

        // ---- note ----------------------------------------------------------------
        noteField = Widgets.editField(context, context.getString(R.string.wallet_note_hint))
        noteField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val noteParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        noteParams.topMargin = Widgets.dp(context, 6)
        column.addView(noteField, noteParams)

        // ---- preview -------------------------------------------------------------
        val info = Widgets.column(context)
        info.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 8)
        )
        info.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline)
        )
        infoLabel = Widgets.text(context, "", R.style.Text_Label)
        infoLabel.setTextColor(Widgets.color(context, R.color.text_secondary))
        info.addView(infoLabel)
        errorLabel = Widgets.text(context, "", R.style.Text_Caption)
        errorLabel.setTextColor(Widgets.color(context, R.color.danger))
        info.addView(errorLabel)
        val infoParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        infoParams.topMargin = Widgets.dp(context, 8)
        column.addView(info, infoParams)

        // ---- actions -------------------------------------------------------------
        val save = Widgets.button(context, context.getString(R.string.wallet_save_movement))
        save.setOnClickListener { save() }
        root.addView(save, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(Widgets.dp(context, 10), Widgets.dp(context, 6), Widgets.dp(context, 10), 0)
        })

        val keypad = KeypadView(
            context,
            arrayOf(
                arrayOf("7", "8", "9", "⌫"),
                arrayOf("4", "5", "6", "C"),
                arrayOf("1", "2", "3", "."),
                arrayOf("0", "00", "000", "⏎")
            )
        ) { key -> onKey(key) }
        root.addView(keypad, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Widgets.dp(context, 116)
        ).apply { setMargins(Widgets.dp(context, 4), Widgets.dp(context, 4), Widgets.dp(context, 4), Widgets.dp(context, 6)) })

        update()
        return root
    }

    private fun accountLabel(): Int = when (kind) {
        WalletEntryKind.WITHDRAW -> R.string.wallet_withdraw_to
        else -> R.string.wallet_account
    }

    private fun optionLabel(account: WalletAccount): String {
        val context = host.activity
        val ledger = Toolbox.wallet.ledger
        val balance = Money.format(ledger.balanceOf(account.id), account.currency)
        return if (account.name.isEmpty()) balance else "${account.name} · $balance"
    }

    private fun selected(): WalletAccount? = candidates.firstOrNull { it.id == selectedId }

    // ---- keypad -----------------------------------------------------------------

    private fun onKey(key: String) {
        when (key) {
            "⏎" -> {
                save()
                return
            }
            "C" -> amount = ""
            "⌫" -> amount = amount.dropLast(1)
            "." -> if (!amount.contains('.')) amount = if (amount.isEmpty()) "0." else "$amount."
            else -> {
                val digits = key.filter { it.isDigit() }
                if (digits.isEmpty()) return
                amount = if (amount == "0") digits else amount + digits
            }
        }
        updating = true
        amountField.setText(amount)
        amountField.setSelection(amountField.text?.length ?: 0)
        updating = false
        update()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!ready) return false
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 && !noteField.hasFocus()) {
            onKey((keyCode - KeyEvent.KEYCODE_0).toString())
            return true
        }
        if ((keyCode == KeyEvent.KEYCODE_PERIOD || keyCode == KeyEvent.KEYCODE_NUMPAD_DOT) && !noteField.hasFocus()) {
            onKey(".")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && amountField.hasFocus()) return false
        if (keyCode == KeyEvent.KEYCODE_DEL && !noteField.hasFocus()) {
            onKey("⌫")
            return true
        }
        if (keyCode == HardwareKeys.KEY_STAR) {
            onKey(".")
            return true
        }
        if (keyCode == HardwareKeys.KEY_POUND) {
            onKey("C")
            return true
        }
        return false
    }

    // ---- preview & save ---------------------------------------------------------

    private fun update() {
        val context = host.activity
        val ledger = Toolbox.wallet.ledger
        val rates = Toolbox.rates()
        val account = selected() ?: return
        val value = Money.parse(amount) ?: 0.0

        currencyLabel.text = account.currency

        val lines = ArrayList<String>()
        var error: String? = null
        when (kind) {
            WalletEntryKind.WITHDRAW -> {
                val main = ledger.mainAccount
                if (main == null) {
                    error = context.getString(R.string.wallet_error_no_main)
                } else {
                    val cost = ledger.withdrawalCost(account.id, value, rates)
                    val rate = ledger.withdrawalRate(account.id, rates) ?: 1.0
                    if (account.currency == main.currency) {
                        lines.add(context.getString(R.string.wallet_preview_same_currency, main.name))
                    } else {
                        lines.add(
                            context.getString(
                                R.string.wallet_preview_cost,
                                Money.format(cost, main.currency),
                                main.name
                            )
                        )
                        lines.add(
                            context.getString(
                                R.string.wallet_preview_rate,
                                Money.format(rate, main.currency, 4),
                                account.currency
                            )
                        )
                    }
                    lines.add(
                        context.getString(
                            R.string.wallet_preview_after,
                            main.name,
                            Money.format(ledger.balanceOf(main.id) - cost, main.currency)
                        )
                    )
                    lines.add(
                        context.getString(
                            R.string.wallet_preview_cash_after,
                            account.name,
                            Money.format(ledger.balanceOf(account.id) + value, account.currency)
                        )
                    )
                    if (value > 0.0 && cost > ledger.balanceOf(main.id) + WalletLedger.TOLERANCE) {
                        error = context.getString(
                            R.string.wallet_error_insufficient,
                            Money.format(ledger.balanceOf(main.id), main.currency),
                            main.name
                        )
                    }
                }
            }
            else -> {
                val balance = ledger.balanceOf(account.id)
                lines.add(
                    context.getString(
                        R.string.wallet_preview_available,
                        Money.format(balance, account.currency),
                        account.name
                    )
                )
                val after = if (kind == WalletEntryKind.EXPENSE) balance - value else balance + value
                lines.add(
                    context.getString(
                        R.string.wallet_preview_after,
                        account.name,
                        Money.format(after, account.currency)
                    )
                )
                if (kind == WalletEntryKind.EXPENSE &&
                    value > balance + WalletLedger.TOLERANCE
                ) {
                    error = context.getString(
                        R.string.wallet_error_insufficient,
                        Money.format(balance, account.currency),
                        account.name
                    )
                }
            }
        }
        infoLabel.text = lines.joinToString("\n")
        errorLabel.text = error ?: ""
    }

    private fun save() {
        val context = host.activity
        if (!ready) return
        val ledger = Toolbox.wallet.ledger
        val account = selected()
        if (account == null) {
            host.showMessage(context.getString(R.string.wallet_error_no_cash))
            return
        }
        val value = Money.parse(amount)
        if (value == null || value <= 0.0) {
            host.showMessage(context.getString(R.string.wallet_error_amount))
            return
        }
        val note = noteField.text?.toString()?.trim() ?: ""
        val now = System.currentTimeMillis()
        val change = when (kind) {
            WalletEntryKind.TOP_UP -> ledger.topUp(account.id, value, note, now)
            WalletEntryKind.EXPENSE -> ledger.expense(account.id, value, note, now)
            WalletEntryKind.WITHDRAW -> ledger.withdraw(account.id, value, note, now, Toolbox.rates())
        }
        when (change) {
            is WalletChange.Done -> {
                Toolbox.wallet.update(change.ledger)
                recordHistory(change.entry, account, ledger)
                host.showMessage(context.getString(R.string.wallet_saved))
                host.pop()
            }
            is WalletChange.Failed -> host.showMessage(errorMessage(change.code, change.detail))
        }
    }

    private fun errorMessage(code: WalletErrorCode, detail: String): String {
        val context = host.activity
        return when (code) {
            WalletErrorCode.AMOUNT -> context.getString(R.string.wallet_error_amount)
            WalletErrorCode.ACCOUNT -> context.getString(R.string.wallet_error_account)
            WalletErrorCode.NO_MAIN -> context.getString(R.string.wallet_error_no_main)
            WalletErrorCode.NO_CASH -> context.getString(R.string.wallet_error_no_cash)
            WalletErrorCode.INSUFFICIENT ->
                context.getString(R.string.wallet_error_insufficient_generic, detail)
        }
    }

    /** The main-currency side of a withdrawal, or null for the other two movements. */
    private fun counterAmount(entry: WalletEntry): Double? =
        if (entry.kind == WalletEntryKind.WITHDRAW) entry.counterAmount else null

    private fun recordHistory(
        entry: WalletEntry,
        account: WalletAccount,
        ledger: WalletLedger
    ) {
        val context = host.activity
        val sign = if (kind == WalletEntryKind.EXPENSE) "-" else "+"
        val summary = when (kind) {
            WalletEntryKind.WITHDRAW -> context.getString(
                R.string.wallet_headline_withdraw,
                account.name
            )
            WalletEntryKind.TOP_UP -> context.getString(R.string.wallet_headline_top_up, account.name)
            WalletEntryKind.EXPENSE -> context.getString(R.string.wallet_headline_expense, account.name)
        }
        Toolbox.store.addHistory(
            HistoryEntry(
                toolId = "wallet",
                toolName = context.getString(R.string.wallet_tool_name),
                title = "$sign${Money.format(entry.amount, account.currency)}",
                summary = summary,
                details = listOfNotNull(
                    entry.note.ifEmpty { null },
                    // local copy: a nullable property of another module is not smart cast
                    counterAmount(entry)?.let { counter ->
                        context.getString(
                            R.string.wallet_withdraw_detail,
                            Money.format(counter, ledger.mainAccount?.currency ?: ""),
                            Money.format(entry.rate ?: 1.0, "", 4)
                        )
                    }
                )
            )
        )
    }
}
