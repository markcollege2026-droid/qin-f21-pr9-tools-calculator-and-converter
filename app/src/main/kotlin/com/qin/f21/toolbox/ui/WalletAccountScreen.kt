package com.qin.f21.toolbox.ui

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import com.qin.f21.toolbox.R
import com.qin.f21.toolbox.Toolbox
import com.qin.f21.toolbox.core.wallet.Money
import com.qin.f21.toolbox.core.wallet.WalletAccount
import com.qin.f21.toolbox.core.wallet.WalletAccountKind

/**
 * Creates or edits one balance of the wallet.
 *
 * A cash pocket may use any currency of the bundled rate table, independently of the main
 * balance, and it can be *separated* from the main balance: a separated pocket keeps its
 * own money but is left out of the combined total. Several pockets may share a currency,
 * so "cash at home", "cash at work" and "holiday money" can all live side by side.
 *
 * @param accountId the account to edit, or null to create a new cash pocket.
 */
class WalletAccountScreen(
    host: ScreenHost,
    private val accountId: String?
) : Screen(host) {

    override val title: String
        get() = host.activity.getString(
            if (existing == null) R.string.wallet_new_account else R.string.wallet_edit_account
        )

    override val icon: String? = "ic_wallet"

    private val existing: WalletAccount?
        get() = accountId?.let { Toolbox.wallet.ledger.account(it) }

    private var currencies: List<String> = emptyList()
    private var currency = "USD"

    private lateinit var nameField: EditText
    private lateinit var currencySpinner: Spinner
    private lateinit var openingField: EditText
    private lateinit var includeSwitch: Switch

    override fun onCreateView(parent: ViewGroup): View {
        val context = host.activity
        val account = existing
        val ledger = Toolbox.wallet.ledger
        currencies = Toolbox.currency.currencies.ifEmpty { listOf("USD") }
        currency = account?.currency
            ?: Toolbox.wallet.ledger.mainAccount?.currency
            ?: Toolbox.currency.base
            ?: currencies.first()

        val root = Widgets.column(context)
        root.setBackgroundColor(Widgets.color(context, R.color.bg))

        val scroll = ScrollView(context).forKeypad()
        val column = Widgets.column(context)
        column.setPadding(
            Widgets.dp(context, 10), Widgets.dp(context, 8),
            Widgets.dp(context, 10), Widgets.dp(context, 12)
        )

        // ---- name ----------------------------------------------------------------
        column.addView(Widgets.caption(context, context.getString(R.string.wallet_account_name)))
        nameField = Widgets.editField(context, context.getString(R.string.wallet_cash_default_name))
        nameField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        nameField.setText(account?.name ?: "")
        column.addView(nameField)

        // ---- currency ------------------------------------------------------------
        column.addView(Widgets.caption(context, context.getString(R.string.wallet_account_currency)))
        currencySpinner = Spinner(context)
        currencySpinner.background = Widgets.roundedBackground(
            context,
            Widgets.color(context, R.color.surface),
            Widgets.color(context, R.color.outline),
            6
        )
        currencySpinner.adapter = ArrayAdapter(
            context, R.layout.item_spinner, R.id.text1, currencies
        )
        currencySpinner.setSelection(currencies.indexOf(currency).coerceAtLeast(0))
        currencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                // a disabled spinner still reports its layout position; the currency of
                // an account with entries must not move
                if (currencySpinner.isEnabled) currencies.getOrNull(position)?.let { currency = it }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        column.addView(currencySpinner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val isMain = account?.kind == WalletAccountKind.MAIN
        val hasEntries = account != null && ledger.entriesFor(account.id).isNotEmpty()
        if (hasEntries) {
            // the recorded amounts are stored in the currency of the account, so changing
            // it would silently relabel every past movement
            currencySpinner.isEnabled = false
            column.addView(Widgets.caption(
                context,
                context.getString(R.string.wallet_currency_locked, ledger.entriesFor(account.id).size)
            ))
        }

        // ---- opening balance -----------------------------------------------------
        val openingLabel = if (isMain) {
            context.getString(R.string.wallet_main_opening)
        } else {
            context.getString(R.string.wallet_opening_balance)
        }
        column.addView(Widgets.caption(context, openingLabel))
        val openingRow = Widgets.row(context)
        openingRow.gravity = Gravity.CENTER_VERTICAL
        openingField = Widgets.numericField(context, "0.00")
        openingField.gravity = Gravity.END
        openingField.setText(Money.format(account?.opening ?: 0.0))
        openingRow.addView(openingField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val suffix = Widgets.text(context, currency, R.style.Text_Subtitle)
        suffix.setTextColor(Widgets.color(context, R.color.text_secondary))
        openingRow.addView(suffix, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = Widgets.dp(context, 8) })
        column.addView(openingRow)
        if (account != null) {
            column.addView(Widgets.caption(
                context,
                context.getString(
                    R.string.wallet_current_balance,
                    Money.format(ledger.balanceOf(account.id), account.currency)
                )
            ))
        }

        // ---- separate from the main balance --------------------------------------
        if (!isMain) {
            val row = Widgets.row(context)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(
                Widgets.dp(context, 10), Widgets.dp(context, 8),
                Widgets.dp(context, 10), Widgets.dp(context, 8)
            )
            row.background = Widgets.roundedBackground(
                context,
                Widgets.color(context, R.color.surface),
                Widgets.color(context, R.color.outline)
            )
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = Widgets.dp(context, 10)
            row.layoutParams = params
            val texts = Widgets.column(context)
            texts.addView(Widgets.text(context, context.getString(R.string.wallet_include_in_total), R.style.Text_Body))
            texts.addView(Widgets.caption(context, context.getString(R.string.wallet_include_hint)))
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            includeSwitch = Switch(context)
            includeSwitch.isChecked = account?.includedInTotal ?: true
            row.addView(includeSwitch, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            column.addView(row)
        }

        scroll.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // ---- actions -------------------------------------------------------------
        val actions = Widgets.row(context)
        val save = Widgets.button(context, context.getString(R.string.action_save))
        save.setOnClickListener { save() }
        actions.addView(save, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (account != null && account.kind != WalletAccountKind.MAIN) {
            actions.addView(Widgets.spacer(context, 8))
            val delete = Widgets.button(context, context.getString(R.string.wallet_delete_account), secondary = true)
            delete.isEnabled = ledger.canRemove(account.id)
            delete.setOnClickListener { delete(account) }
            actions.addView(delete, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (!ledger.canRemove(account.id)) {
                column.addView(Widgets.caption(context, context.getString(R.string.wallet_delete_blocked)))
            }
        }
        root.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Widgets.dp(context, 10), Widgets.dp(context, 6), Widgets.dp(context, 10), Widgets.dp(context, 10)) })

        return root
    }

    private fun save() {
        val context = host.activity
        val account = existing
        val name = nameField.text?.toString()?.trim().orEmpty()
            .ifEmpty { context.getString(R.string.wallet_cash_default_name) }
        val opening = Money.parse(openingField.text?.toString() ?: "")
        if (opening == null) {
            host.showMessage(context.getString(R.string.wallet_error_amount_or_zero))
            return
        }
        val updated = if (account == null) {
            WalletAccount(
                name = name,
                currency = currency,
                opening = opening,
                includedInTotal = true,
                kind = WalletAccountKind.CASH,
                createdAt = System.currentTimeMillis()
            )
        } else {
            account.copy(
                name = name,
                currency = if (Toolbox.wallet.ledger.entriesFor(account.id).isNotEmpty()) {
                    account.currency
                } else {
                    currency
                },
                opening = opening,
                includedInTotal = if (account.kind == WalletAccountKind.MAIN) true else includeSwitch.isChecked
            )
        }
        Toolbox.wallet.update(Toolbox.wallet.ledger.withAccount(updated))
        host.showMessage(context.getString(R.string.wallet_account_saved))
        host.pop()
    }

    private fun delete(account: WalletAccount) {
        val context = host.activity
        if (!Toolbox.wallet.ledger.canRemove(account.id)) {
            host.showMessage(context.getString(R.string.wallet_delete_blocked))
            return
        }
        android.app.AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.wallet_delete_account))
            .setMessage(context.getString(R.string.wallet_delete_account_confirm, account.name))
            .setNegativeButton(context.getString(R.string.no), null)
            .setPositiveButton(context.getString(R.string.yes)) { _, _ ->
                Toolbox.wallet.update(Toolbox.wallet.ledger.removeAccount(account.id))
                host.showMessage(context.getString(R.string.wallet_account_deleted))
                host.pop()
            }
            .show()
    }
}
