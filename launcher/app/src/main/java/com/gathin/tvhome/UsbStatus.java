package com.gathin.tvhome;

import android.content.Context;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Short USB accessory labels for the home/screensaver stack, each with a green status pill.
 */
final class UsbStatus {

    private UsbStatus() {
    }

    static void bind(LinearLayout stack) {
        if (stack == null) return;
        List<String> labels = labels(stack.getContext());
        stack.removeAllViews();
        if (labels.isEmpty()) {
            stack.setVisibility(View.GONE);
            return;
        }
        stack.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(stack.getContext());
        for (String label : labels) {
            View row = inflater.inflate(R.layout.item_usb_status, stack, false);
            ((TextView) row.findViewById(R.id.usbLabel)).setText(label);
            stack.addView(row);
        }
    }

    static List<String> labels(Context ctx) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        addUsbDevices(ctx, out);
        addVolumes(ctx, out);
        addMice(ctx, out);
        return new ArrayList<>(out);
    }

    private static void addUsbDevices(Context ctx, LinkedHashSet<String> out) {
        try {
            UsbManager usb = (UsbManager) ctx.getSystemService(Context.USB_SERVICE);
            if (usb == null) return;
            Map<String, UsbDevice> devices = usb.getDeviceList();
            if (devices == null) return;
            for (UsbDevice device : devices.values()) {
                if (device == null) continue;
                if (isHidPointer(device)) {
                    out.add("Mouse input");
                } else {
                    out.add(shortStorageName(device.getManufacturerName(), device.getProductName()));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void addVolumes(Context ctx, LinkedHashSet<String> out) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            StorageManager sm = (StorageManager) ctx.getSystemService(Context.STORAGE_SERVICE);
            if (sm == null) return;
            for (StorageVolume volume : sm.getStorageVolumes()) {
                if (!volume.isRemovable()) continue;
                if (!Environment.MEDIA_MOUNTED.equals(volume.getState())) continue;
                CharSequence desc = volume.getDescription(ctx);
                out.add(shortStorageName(desc == null ? null : desc.toString(), null));
            }
        } catch (Exception ignored) {
        }
    }

    private static void addMice(Context ctx, LinkedHashSet<String> out) {
        try {
            InputManager im = (InputManager) ctx.getSystemService(Context.INPUT_SERVICE);
            if (im == null) return;
            for (int id : im.getInputDeviceIds()) {
                InputDevice device = im.getInputDevice(id);
                if (device == null || device.isVirtual() || !device.isExternal()) continue;
                int sources = device.getSources();
                boolean mouse = (sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
                        || (sources & InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE
                        || (sources & InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD;
                if (mouse) out.add("Mouse input");
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isHidPointer(UsbDevice device) {
        if (device.getDeviceClass() == UsbConstants.USB_CLASS_HID
                && device.getDeviceProtocol() == 2) {
            return true;
        }
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface == null) continue;
            if (iface.getInterfaceClass() != UsbConstants.USB_CLASS_HID) continue;
            int protocol = iface.getInterfaceProtocol();
            if (protocol == 2 || protocol == 0) return true;
        }
        return false;
    }

    /** One crisp name: "Jet Flash", "Mouse input", or the first word of the drive name. */
    static String shortStorageName(String manufacturer, String product) {
        String raw = firstNonEmpty(manufacturer, product);
        if (raw.isEmpty()) return "USB";
        String compact = raw.replaceAll("\\s+", " ").trim();
        String lower = compact.toLowerCase(Locale.US);
        String squeezed = lower.replace(" ", "");
        if (squeezed.contains("jetflash") || lower.contains("jet flash") || squeezed.startsWith("jet")) {
            return "Jet Flash";
        }
        if (lower.contains("mouse") || lower.contains("touchpad") || lower.contains("trackpad")) {
            return "Mouse input";
        }
        int space = compact.indexOf(' ');
        return space < 0 ? compact : compact.substring(0, space);
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return "";
    }
}
