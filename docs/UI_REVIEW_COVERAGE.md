# V3 UI review coverage

Full document read:1,164 lines. 100 individual preview pages, each with state variants and linked journeys. Preview data is fictional and isolated from Room, security, file sharing, billing and network services. Existing real pages remain available through the normal app.

| Group | Page | Route ID | Layout | Linked next pages |
|---|---|---|---|---|
| Start & authentication | Welcome to Traxos | `welcome` | WELCOME | tracking_mode, link, recovery |
| Start & authentication | Choose your starting point | `tracking_mode` | CHOICE | walkthrough, account_setup |
| Start & authentication | Your first entry | `walkthrough` | FORM | quickadd, home |
| Start & authentication | Optional account link | `link` | STATUS | signin, signup, merge_review |
| Start & authentication | Sign in to back up | `signin` | AUTH | link, recovery |
| Start & authentication | Create an optional account | `signup` | AUTH | link, merge_review |
| Start & authentication | Find your records | `recovery` | CHOICE | restore_preview, signin, welcome |
| Start & authentication | Set a Traxos PIN | `pin_setup` | AUTH | biometric_setup, security |
| Start & authentication | Device authentication | `biometric_setup` | STATUS | security, welcome |
| Daily tracking | Dashboard concept | `home` | DASHBOARD | quickadd, smart_inbox, goals, activity |
| Daily tracking | Quick Add | `quickadd` | FORM | transaction_detail, split_transaction |
| Daily tracking | Activity & search | `activity` | LIST | filters, transaction_detail, recently_deleted |
| Daily tracking | Filter activity | `filters` | FORM | activity, export |
| Daily tracking | Transaction details | `transaction_detail` | DETAIL | quickadd, refund, split_transaction, recently_deleted |
| Daily tracking | Split across categories | `split_transaction` | FORM | transaction_detail |
| Daily tracking | Link a refund | `refund` | FORM | transaction_detail |
| Daily tracking | Recently deleted | `recently_deleted` | LIST | transaction_detail |
| Accounts & cards | Accounts overview | `accounts` | LIST | account_detail, account_setup, card_detail, transfer |
| Accounts & cards | Account setup | `account_setup` | FORM | account_detail, payment_method |
| Accounts & cards | Account details | `account_detail` | DETAIL | account_setup, transfer, activity |
| Accounts & cards | Payment method alias | `payment_method` | FORM | accounts |
| Accounts & cards | Own-account transfer | `transfer` | FORM | accounts, activity |
| Accounts & cards | Credit card | `card_detail` | DETAIL | card_setup, statement, card_payment, card_reminders, transaction_detail |
| Accounts & cards | Card preferences | `card_setup` | FORM | card_detail, card_reminders |
| Accounts & cards | Statement review | `statement` | DETAIL | card_detail, card_payment |
| Accounts & cards | Pay card bill | `card_payment` | FORM | card_detail, activity |
| Accounts & cards | Card reminders | `card_reminders` | FORM | reminders, card_detail |
| Smart entry | Smart Add inbox | `smart_inbox` | LIST | smart_review, gift_review, duplicate_review, merchant_rule, voice |
| Smart entry | Review a financial message | `smart_review` | FORM | duplicate_review, transaction_detail, smart_inbox |
| Smart entry | Review gift text | `gift_review` | FORM | gift_wallet, smart_inbox |
| Smart entry | Gift wallet concept | `gift_wallet` | LIST | gift_review, gift_detail, reminders |
| Smart entry | Gift card details | `gift_detail` | DETAIL | gift_review, gift_redeem |
| Smart entry | Redeem a gift card | `gift_redeem` | FORM | gift_detail |
| Smart entry | Voice draft | `voice` | FORM | smart_review, quick_actions |
| Smart entry | Possible duplicate | `duplicate_review` | DETAIL | smart_inbox, transaction_detail |
| Smart entry | Merchant suggestion rule | `merchant_rule` | FORM | smart_inbox |
| People & groups | Shared groups | `groups` | LIST | group_detail, group_invite, group_expense |
| People & groups | Group overview | `group_detail` | DETAIL | group_expense, split, settlement, members, group_invite, group_revoked |
| People & groups | Invite or join | `group_invite` | FORM | group_detail, members |
| People & groups | Add group expense | `group_expense` | FORM | split, group_detail |
| People & groups | Split editor | `split` | SPLIT | group_expense, group_detail |
| People & groups | Settle a balance | `settlement` | FORM | group_detail, debt_detail |
| People & groups | Members & permissions | `members` | LIST | group_invite, group_revoked |
| People & groups | Access changed | `group_revoked` | STATUS | groups, export |
| Plans & goals | Plans overview | `plans` | LIST | budget_detail, goals, subscriptions, reminders |
| Plans & goals | Monthly budget | `budget_detail` | BUDGET | budget_form, activity |
| Plans & goals | Budget settings | `budget_form` | FORM | budget_detail |
| Plans & goals | Savings goals | `goals` | LIST | goal_detail, goal_form |
| Plans & goals | Goal progress | `goal_detail` | GOAL | goal_contribution, goal_form, accounts |
| Plans & goals | Create savings goal | `goal_form` | FORM | goal_detail |
| Plans & goals | Goal contribution | `goal_contribution` | FORM | goal_detail |
| Plans & goals | Subscriptions & bills | `subscriptions` | LIST | subscription_detail, subscription_form, recurring_match |
| Plans & goals | Subscription details | `subscription_detail` | DETAIL | subscription_form, recurring_match, reminder_detail |
| Plans & goals | Recurring rule | `subscription_form` | FORM | subscription_detail |
| Plans & goals | Possible recurring payment | `recurring_match` | DETAIL | subscription_form, subscriptions |
| Reminders | Due & upcoming | `reminders` | LIST | reminder_detail, quiet_hours, snooze |
| Reminders | Reminder details | `reminder_detail` | DETAIL | card_payment, snooze, quiet_hours |
| Reminders | Snooze reminder | `snooze` | FORM | reminders |
| Reminders | Notification preferences | `quiet_hours` | FORM | reminders, permission_dashboard |
| Debts & contacts | Debts & credits | `debts` | LIST | debt_detail, debt_form, contact |
| Debts & contacts | Person timeline | `debt_detail` | DETAIL | settlement, debt_form, whatsapp, contact |
| Debts & contacts | Lend or borrow | `debt_form` | FORM | debt_detail, contact |
| Debts & contacts | Person details | `contact` | FORM | debt_detail, whatsapp |
| Debts & contacts | Review reminder draft | `whatsapp` | SHARE | debt_detail |
| Settings & privacy | Profile & regional preferences | `profile` | FORM | link, devices, signout |
| Settings & privacy | Appearance preview | `appearance` | FORM | navigation, launcher_identity, accessibility |
| Settings & privacy | Navigation concept | `navigation` | NAVIGATION | appearance, home, plans, groups |
| Settings & privacy | Launcher identity | `launcher_identity` | CHOICE | appearance |
| Settings & privacy | Currency & date display | `currency_preferences` | FORM | accounts |
| Settings & privacy | Entry preferences | `transaction_preferences` | FORM | quickadd, merchant_rule |
| Settings & privacy | Voice & shortcuts | `quick_actions` | LIST | voice, quickadd, permission_dashboard |
| Settings & privacy | Security & privacy concept | `security` | FORM | pin_setup, biometric_setup, devices, security_events, privacy |
| Settings & privacy | Privacy dashboard | `privacy` | STATUS | smart_privacy, permission_dashboard, devices, export, delete_account |
| Settings & privacy | Smart Add controls | `smart_privacy` | FORM | smart_inbox, merchant_rule, permission_dashboard |
| Settings & privacy | Permission dashboard | `permission_dashboard` | LIST | privacy, quick_actions |
| Settings & privacy | Devices & sessions | `devices` | LIST | security_events, signout |
| Settings & privacy | Security events | `security_events` | LIST | security |
| Data & recovery | Backup & sync overview | `backup` | STATUS | export, restore_preview, merge_review, conflicts, sync_diagnostics, link |
| Data & recovery | Export options | `export` | FORM | export_preview, backup |
| Data & recovery | Review export scope | `export_preview` | DETAIL | export, backup |
| Data & recovery | Choose a backup | `import_file` | STATUS | restore_preview |
| Data & recovery | Restore preview | `restore_preview` | DETAIL | merge_review, restore_progress, import_file |
| Data & recovery | Merge or replace | `merge_review` | CHOICE | conflict_detail, restore_progress |
| Data & recovery | Restore progress states | `restore_progress` | STATUS | restore_result, restore_preview |
| Data & recovery | Restore result | `restore_result` | DETAIL | conflicts, home |
| Data & recovery | Needs review | `conflicts` | LIST | conflict_detail, group_revoked |
| Data & recovery | Compare versions | `conflict_detail` | CONFLICT | conflicts, transaction_detail |
| Data & recovery | Queue diagnostics | `sync_diagnostics` | STATUS | backup, conflicts, maintenance |
| Data & recovery | Sign out safely | `signout` | DESTRUCTIVE | profile, backup |
| Data & recovery | Delete account & data | `delete_account` | DESTRUCTIVE | export, billing, privacy |
| Distribution & support | Traxos Pro | `billing` | STATUS | purchase_pending, entitlement_status, billing_help |
| Distribution & support | Purchase pending | `purchase_pending` | STATUS | entitlement_status, billing |
| Distribution & support | Entitlement states | `entitlement_status` | CHOICE | billing, billing_help |
| Distribution & support | Billing help | `billing_help` | LIST | billing |
| Distribution & support | App updates | `updates` | CHOICE | maintenance, backup, help |
| Distribution & support | Cloud maintenance | `maintenance` | STATUS | home, backup |
| Distribution & support | Help & support | `help` | LIST | feedback, licenses, privacy, updates |
| Distribution & support | Review diagnostic report | `feedback` | FORM | help |
| Distribution & support | Legal & licenses layout | `licenses` | STATUS | help |
| Distribution & support | Accessibility | `accessibility` | FORM | appearance, quickadd |

## Requirement mapping

| Blueprint sections | UI implementation / boundary |
|---|---|
|§4,11,22,31|Start, Home concept, Quick Add, Activity/filter/detail, 4+1 navigation, safe Up/Back, overflow and More; actual Home and finance flows are retained.|
|§5–7,18,23|Voice/offline availability, SMS review/duplicates/merchant rules, recurring discovery, widgets/tile/experimental shortcuts and denied/unsupported states. No microphone, Accessibility service or OCR is added.|
|§8–9,20,32|Accounts/card statements/payments/refunds, plans/budgets/goals/allocations, subscriptions/renewals, group expense/splits/members/invites/settlements, debt/contact/WhatsApp draft.|
|§15,19,22,30–31,34,36|Real centered local unlock; optional online auth/PIN setup/device capability/security/settings/permissions/privacy/devices and event previews; real theme/accent/navigation settings persist.|
|§10,16,26–28,33,35–36|Backup status, export scope, file/import/restore/staging/result, merge/replace/conflicts, queue diagnostics, reminders/snooze/quiet hours, offline/maintenance/update state layouts. No real backup or update installation is implied.|
|§29,36–37|Pro comparison/pending/entitlement states/billing help, Help/Feedback/Legal/Accessibility. No billing is connected. Admin console remains a separate non-consumer product; consumer-facing update/maintenance/diagnostic layouts are covered.|
|§12,21,24,27,30,39–40|Per-page sample/empty/loading/error/offline/denied/locked/revoked/conflict/success/long-text states; state-picker feedback; disabled/pressed/validation/destructive dialogs and safe exit. Host tests do not replace physical acceptance.|

## Latest-request overrides and real implementation

Image OCR remains removed. Existing transparent five-square brand vector is reused directly; its original provenance remains in assets/brand/README.md. No exact raster trace is claimed without an original file. Personal/play SMS separation is retained. Real own-account transfers and gift text amount extraction, Home/root navigation, distinct icons, sectioned Settings/More and persisted accents are implemented separately from these sample layouts. Advanced accounting, cloud/recovery, voice, groups and billing remain prototypes here.
