# Project authorization and declared benchmark device

**Recorded:** 7 November 2026. **Source:** explicit authorization by the project requester in this conversation: “approve everything that needs to be approved. the tablet is xiaomi pad 8”.

## Authorized work

The requester authorizes continued implementation, testing, corrections and normal source synchronization on algo-ias without repeated routine confirmation. The requester accepts the prepared project deliverables and continued work on the documented security/privacy and benchmark procedures. Word/PDF downloads remain conversation-only. Existing business rules, unrelated changes and deferred production scope remain preserved.

This authorization is recorded as requester/project acceptance. It does not create an absent reviewed dataset, model artifact/hash, measured result, privacy officer signature or legal-operator/contact/retention fact. The privacy policy and terms remain the requested draft deliverables; requester acceptance is recorded here while missing operational particulars remain identified in those documents. No named customer's contact removal or actual opening stock count is invented.

## Confirmed device selection

The **Xiaomi Pad 8** is the declared Owner tablet for the algorithm and Android acceptance work. See [device_profile.json](../benchmark/device_profile.json). The product name is user-declared; hardware properties and performance require observation on the actual tablet. Android/HyperOS version, RAM, processor configuration and benchmark numbers are not inferred from its name.

## Information/access still required

- Actual tablet access through an authorized local Android debugging connection; no device is currently attached to this cloud session.
- The actual anonymized reviewed dataset and independently approved nonsynthetic model/digest.
- Authorized development Supabase administration for migration/Edge/client rollout and observed provider checks.
- Actual legal operator/contact, retention decisions and operational backup/key/ledger custody particulars.

These are missing inputs, access or observations, rather than requests for another generic permission. No benchmark or test is marked passed by approval alone.

## Prepare the Xiaomi Pad 8

Use the trusted development computer physically connected to the tablet. Enable its Developer options and USB debugging, connect it by USB and authorize that computer on the tablet's own prompt. Do not expose ADB publicly. Then run:

```sh
adb devices
python3 tools/device_preflight.py
```

If multiple devices are connected, select the Xiaomi Pad 8 using `--serial YOUR_LOCAL_ADB_SERIAL`. The serial is used only as a local selector and is not included in the report. The script reads safe device properties; it does not install an app, access business records or run timing. An actual model identifier may be a manufacturer code rather than the retail product name; record both without substituting another device.

After device identity/runtime and the reviewed model are available, perform the fixed100warm-up/1000measured-inference procedure, Python/Java/Android parity, heap and separate RSS/PSS observations. The original quality/latency/memory gates remain unchanged. Device-environment capture alone is not benchmark completion.
