package com.example.aquaquencherspos;

import android.app.AlertDialog;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/** Owner Settings: inventory, staff accounts and account security (A-11). */
public final class SettingsScreens {

    private static final int MIN_PASSWORD = 15;

    private SettingsScreens() {
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    /** Figma "03 · Settings". */
    public static class Settings extends BaseScreen {
        @Override
        String section() {
            return "Settings";
        }

        private void group(String title, String[] labels, Runnable[] actions) {
            page.addView(Ui.spaced(c, Ui.cardTitle(c, title), 8));
            for (int n = 0; n < labels.length; n++) {
                Runnable action = actions[n];
                page.addView(Ui.spaced(c, Ui.listButton(c, labels[n], v -> action.run()), 8));
            }
            // A plain View with wrap_content height would take all the remaining space.
            page.addView(new android.view.View(c), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 4)));
        }

        @Override
        void build() {
            head("Settings", "Staff accounts, basic inventory and account access");
            group("Inventory", new String[]{"Inventory"}, new Runnable[]{() -> go(new Inventory())});
            group("Staff accounts", new String[]{"Staff accounts"}, new Runnable[]{() -> go(new Staff())});
            group("Account security", new String[]{"Change password"}, new Runnable[]{() -> go(new ChangePassword())});
            group("Account Actions", new String[]{"Log out", "Back to dashboard"}, new Runnable[]{
                    () -> shell().signOut(),
                    () -> shell().openRoot(new DashboardScreen())
            });
        }
    }

    /** Figma "01 · Inventory". There is deliberately no stock-adjustment control. */
    public static class Inventory extends BaseScreen {
        @Override
        String section() {
            return "Settings";
        }

        @Override
        void build() {
            head("Inventory", "Basic filled and empty stock with automatic transaction updates");
            LinearLayout holder = new LinearLayout(c);
            holder.setOrientation(LinearLayout.VERTICAL);
            page.addView(holder);
            call("inventory_status", new JSONObject(), inv -> {
                LinearLayout stock = Ui.titledCard(c, "Gallon stock · Updated " + Fmt.stamp(s(inv, "updated_at")));
                FlowGrid grid = FlowGrid.stats(c, 4);
                grid.addView(Ui.stat(c, "Filled available", String.valueOf(i(inv, "filled_available"))));
                grid.addView(Ui.stat(c, "Empty", String.valueOf(i(inv, "empty"))));
                grid.addView(Ui.stat(c, "Reserved filled", String.valueOf(i(inv, "reserved_filled"))));
                stock.addView(grid);
                holder.addView(stock);

                LinearLayout pending = Ui.titledCard(c, "Pending deliveries");
                pending.addView(Ui.spaced(c, Ui.stat(c, "Pending delivery", String.valueOf(i(inv, "pending_delivery"))), 12));
                Table table = new Table(c, new String[]{"Customer", "Pending gallons", "Original date"},
                        new float[]{305, 305, 294});
                pending.addView(Ui.spaced(c, table, 12));
                pending.addView(WrapRow.of(c, Ui.secondary(c, "Carry-over Details",
                        v -> go(new FulfillmentScreens.CarryOver()))));
                holder.addView(pending);
                holder.addView(Ui.body(c, "Ready for Delivery converts empty stock and reserves filled gallons for that "
                        + "order once. Deliveries use their own reservation; Walk-in sales use unreserved Filled available only."));

                load("orders_view?select=customer_name,pending_gallons,order_date&status=eq.Undelivered&order=order_date.asc", rows -> {
                    if (rows.length() == 0) {
                        table.showEmpty("No carry-over gallons.");
                    }
                    for (int n = 0; n < rows.length(); n++) {
                        JSONObject o = rows.optJSONObject(n);
                        table.addRow(null, new String[]{s(o, "customer_name"),
                                String.valueOf(i(o, "pending_gallons")), Fmt.date(s(o, "order_date"))}, null);
                    }
                });
            });
        }
    }

    /** Figma "01 · Staff". */
    public static class Staff extends BaseScreen {
        private Table table;

        @Override
        String section() {
            return "Settings";
        }

        @Override
        void build() {
            head("Staff Accounts", "Owner-managed access for station personnel");
            LinearLayout card = Ui.titledCard(c, "Accounts and access");
            card.addView(Ui.spaced(c, WrapRow.of(c, Ui.primary(c, "Add Staff", v -> go(new StaffForm()))), 16));
            table = new Table(c, new String[]{"Name / Username", "Role", "Status", "Access"},
                    new float[]{228, 228, 229, 219});
            card.addView(Ui.spaced(c, table, 12));
            card.addView(Ui.spaced(c, Ui.body(c, "Staff may record payments. Opening stock, account administration, "
                    + "expenses and financial reports require Owner or designated administrator authority."), 12));
            card.addView(WrapRow.of(c, Ui.secondary(c, "Edit Staff", v -> edit())));
            page.addView(card);

            load("profiles?select=id,role,username,full_name,status&order=role.asc,full_name.asc", this::render);
        }

        private void render(JSONArray rows) {
            table.clear();
            for (int n = 0; n < rows.length(); n++) {
                JSONObject p = rows.optJSONObject(n);
                boolean owner = "owner".equals(s(p, "role"));
                table.addRow(p, new String[]{
                        s(p, "full_name") + " / " + s(p, "username"), owner ? "Owner" : "Staff", s(p, "status"),
                        owner ? "All modules" : "Customers, orders, pickup, delivery, walk-ins, payments"
                }, new int[]{0, 0, Ui.statusColor(s(p, "status")), 0});
            }
        }

        private void edit() {
            JSONObject row = table.selected();
            if (row == null) {
                Dialogs.correct(c, "Select a staff account.", "Tap a staff account in the list first.");
            } else if (!"staff".equals(s(row, "role"))) {
                Dialogs.correct(c, "Select a staff account.", "The Owner account is not edited here.");
            } else {
                go(withId(new StaffForm(), s(row, "id")));
            }
        }
    }

    /** Figma "02 · Add Staff", including the MFA check before saving. */
    public static class StaffForm extends BaseScreen {
        private Field name;
        private Field username;
        private Field contact;
        private Field status;
        private Field password;

        @Override
        String section() {
            return "Settings";
        }

        @Override
        void build() {
            boolean editing = argId() != null;
            head("Add or Edit Staff", "Manage a staff account");
            LinearLayout card = Ui.titledCard(c, "Staff account");
            FlowGrid form = FlowGrid.form(c);
            name = Field.text(c, "Full name", null);
            username = Field.plain(c, "Username", null);
            contact = Field.phone(c, "Contact number", null);
            status = Field.dropdown(c, "Account status", "Active", () -> Dialogs.options(c, "Account status",
                    new String[]{"Active", "Inactive"}, pick -> {
                        status.setValue(pick);
                        status.setValueColor(Ui.statusColor(pick));
                    }));
            status.setValueColor(Ui.SUCCESS);
            password = Field.password(c, editing ? "Temporary password (leave blank to keep)" : "Temporary password");
            form.addView(name);
            form.addView(username);
            form.addView(contact);
            form.addView(Field.readOnly(c, "Role", "Staff"));
            form.addView(status);
            form.addView(password);
            card.addView(Ui.spaced(c, form, 12));
            card.addView(Ui.spaced(c, Ui.body(c, "Use at least 15 characters for the temporary password. "
                    + "Saving account changes requires Owner verification."), 16));
            card.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Save Staff Account", v -> submit())));
            page.addView(card);

            if (editing) {
                loadOne("profiles?select=*&id=eq." + argId(), p -> {
                    name.setValue(s(p, "full_name"));
                    username.setValue(s(p, "username"));
                    contact.setValue(Fmt.contact(s(p, "contact_number")));
                    status.setValue(s(p, "status"));
                    status.setValueColor(Ui.statusColor(s(p, "status")));
                });
            }
        }

        private void submit() {
            boolean creating = argId() == null;
            String digits = contact.value().replaceAll("[^0-9]", "");
            String secret = password.rawValue();
            if (name.value().isEmpty() || username.value().isEmpty() || contact.value().isEmpty()
                    || (creating && secret.isEmpty())) {
                Dialogs.correct(c, "Complete the required fields.",
                        "Enter Full name, Username, Contact number and Temporary password. No staff account was created.");
                return;
            }
            if (digits.length() != 11) {
                Dialogs.correct(c, "Please enter a valid contact number. Use 11 digits.",
                        "No staff account was saved. Correct Contact number before saving again.");
                return;
            }
            if (!secret.isEmpty() && length(secret) < MIN_PASSWORD) {
                Dialogs.correct(c, "Use at least 15 characters.",
                        "The new or temporary password is too short. No password or account change was saved.");
                return;
            }
            Mfa.verify(c, () -> send(digits, secret), this::failed);
        }

        private void send(String digits, String secret) {
            AlertDialog busy = Dialogs.saving(c, "Saving staff account…", "Please wait while the account is saved.");
            JSONObject body = params("action", "save_staff", "id", argId(), "full_name", name.value(),
                    "username", username.value(), "contact_number", digits, "status", status.value(),
                    "temporary_password", secret);
            Api.identity(body, r -> {
                busy.dismiss();
                if (isAdded()) {
                    Dialogs.done(c, "Staff account saved.",
                            s(r, "full_name") + " · " + s(r, "username") + "\n"
                                    + Fmt.contact(s(r, "contact_number")) + " · Staff · " + s(r, "status"),
                            this::back);
                }
            }, error -> {
                busy.dismiss();
                failed(error);
            });
        }
    }

    /** Figma "04 · Change Password". */
    public static class ChangePassword extends BaseScreen {
        private Field current;
        private Field next;
        private Field confirm;

        @Override
        String section() {
            return "Settings";
        }

        @Override
        void build() {
            head("Change Password", "Update your Owner account password");
            LinearLayout card = Ui.titledCard(c, "Change your password");
            FlowGrid form = FlowGrid.form(c);
            current = Field.password(c, "Current password");
            next = Field.password(c, "New password");
            form.addView(current);
            form.addView(next);
            card.addView(Ui.spaced(c, form, 12));
            confirm = Field.password(c, "Confirm new password");
            card.addView(Ui.spaced(c, confirm, 12));
            card.addView(Ui.spaced(c, Ui.body(c, "Use at least 15 characters. "
                    + "Enter your current password, new password and confirmation."), 16));
            card.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Save password", v -> submit())));
            page.addView(card);
        }

        private void submit() {
            if (current.rawValue().isEmpty() || next.rawValue().isEmpty() || confirm.rawValue().isEmpty()) {
                Dialogs.correct(c, "Complete the required fields.",
                        "Enter your current password, new password and confirmation.");
                return;
            }
            if (length(next.rawValue()) < MIN_PASSWORD) {
                Dialogs.correct(c, "Use at least 15 characters.",
                        "The new or temporary password is too short. No password or account change was saved.");
                return;
            }
            if (!next.rawValue().equals(confirm.rawValue())) {
                Dialogs.correct(c, "New passwords do not match.",
                        "Your password was not changed. Enter the same new password in both new-password fields.");
                return;
            }
            AlertDialog busy = Dialogs.saving(c, "Saving password…", "Please wait while your password is updated.");
            JSONObject body = params("action", "change_password", "current_password", current.rawValue(),
                    "new_password", next.rawValue());
            Api.identity(body, r -> {
                busy.dismiss();
                if (isAdded()) {
                    Dialogs.done(c, "Password updated.",
                            "The valid password change has been saved. Your account details and current session are retained.",
                            this::back);
                }
            }, error -> {
                busy.dismiss();
                failed(error);
            });
        }
    }
}
