#!/usr/bin/env python3
"""One-off provisioning: create an iOS signing certificate + provisioning profile
via the App Store Connect API using an API key (.p8). No Mac/Xcode required.

Outputs (into --output-dir):
  cert.p12.b64  base64 of the .p12 bundle (private key + Apple-signed cert)
  profile.b64   base64 of the .mobileprovision profile
  p12_password.txt  password used to protect the .p12
  SETUP.md      instructions for turning these into GitHub repo secrets

Prerequisite: App ID (bundle identifier) must exist on the developer portal.
Run through .github/workflows/ios-signing.yml, not directly by hand.
"""

import argparse
import base64
import json
import os
import secrets as pyrandom
import subprocess
import sys
import time
import urllib.error
import urllib.request

API = "https://api.appstoreconnect.apple.com"
AUD = "appstoreconnect-v1"

CERT_TYPES = {
    "appstore": "IOS_DISTRIBUTION",
    "adhoc": "IOS_DISTRIBUTION",
    "development": "IOS_DEVELOPMENT",
}
PROFILE_TYPES = {
    "appstore": "IOS_APP_STORE",
    "adhoc": "IOS_APP_ADHOC",
    "development": "IOS_APP_DEVELOPMENT",
}


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def run(cmd: list, **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, check=True, capture_output=True, text=True, **kwargs)


def make_jwt(private_key_path: str, key_id: str, issuer_id: str) -> str:
    header = b64url(json.dumps({"alg": "ES256", "kid": key_id, "typ": "JWT"}).encode())
    now = int(time.time())
    payload = b64url(
        json.dumps({"iss": issuer_id, "exp": now + 1200, "aud": AUD}).encode()
    )
    signing_input = f"{header}.{payload}".encode()
    sig_der = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", private_key_path],
        check=True,
        capture_output=True,
        input=signing_input,
    ).stdout
    # ECDSA DER signature (SEQUENCE { INTEGER r, INTEGER s }) -> raw r||s (64 bytes)
    assert sig_der[0] == 0x30

    def read_len(i):
        l = sig_der[i]
        i += 1
        if l & 0x80:
            n = l & 0x7F
            l = int.from_bytes(sig_der[i : i + n], "big")
            i += n
        return i, l

    i, _ = read_len(1)

    def read_int(i):
        assert sig_der[i] == 0x02
        i += 1
        i, l = read_len(i)
        v = int.from_bytes(sig_der[i : i + l], "big")
        return v, i + l

    r, i = read_int(i)
    s, _ = read_int(i)
    raw = r.to_bytes(32, "big") + s.to_bytes(32, "big")
    return f"{signing_input.decode()}.{b64url(raw)}"


def api(token: str, method: str, path: str, body: dict = None) -> dict:
    url = f"{API}{path}"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }
    data = (
        json.dumps({"data": body}).encode() if body is not None and method == "POST"
        else json.dumps(body).encode() if body is not None else None
    )
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
    except urllib.error.HTTPError as e:
        raw = e.read()
        raise RuntimeError(
            f"App Store Connect API {method} {path} failed: HTTP {e.code}\n"
            f"{raw.decode()[:1000]}"
        )
    return json.loads(raw) if raw else {}


def find_or_create_bundle_id(token: str, identifier: str) -> str:
    data = api(token, "GET", f"/v1/bundleIds?filter%5Bidentifier%5D={identifier}")
    ids = [b["id"] for b in data.get("data", [])]
    if ids:
        return ids[0]
    created = api(
        token,
        "POST",
        "/v1/bundleIds",
        {
            "type": "bundleIds",
            "attributes": {"name": identifier, "identifier": identifier, "platform": "IOS"},
        },
    )
    return created["data"]["id"]


def register_devices(token: str, devices: list) -> list:
    for udid, name in devices:
        api(
            token,
            "POST",
            "/v1/devices",
            {"type": "devices", "attributes": {"name": name, "udid": udid, "platform": "IOS"}},
        )
        print(f"Registered device: {name} ({udid})")
    data = api(token, "GET", "/v1/devices?filter%5Bplatform%5D=IOS&limit=100")
    return [d["id"] for d in data.get("data", [])]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--key-id", required=True)
    ap.add_argument("--issuer-id", required=True)
    ap.add_argument("--private-key", required=True, help="path to AuthKey_*.p8")
    ap.add_argument("--bundle-id", required=True)
    ap.add_argument("--cert-type", default="appstore", choices=list(CERT_TYPES))
    ap.add_argument("--devices", default="", help="name=UDID,name=UDID (ad-hoc/development only)")
    ap.add_argument("--output-dir", default="build/signing")
    ap.add_argument("--name", default="ZWL iOS Distribution")
    args = ap.parse_args()

    os.makedirs(args.output_dir, exist_ok=True)
    token = make_jwt(args.private_key, args.key_id, args.issuer_id)

    cert_type = CERT_TYPES[args.cert_type]
    profile_type = PROFILE_TYPES[args.cert_type]

    # 1. Private key + CSR
    capath = os.path.join(args.output_dir, "key.pem")
    csr = os.path.join(args.output_dir, "signing.csr")
    run(["openssl", "genrsa", "-out", capath, "2048"])
    run(["openssl", "req", "-new", "-key", capath, "-out", csr, "-subj", f"/CN={args.name}"])
    with open(csr) as f:
        csr_content = f.read()

    # 2. Certificate
    created = api(
        token,
        "POST",
        "/v1/certificates",
        {
            "type": "certificates",
            "attributes": {"certificateType": cert_type, "csrContent": csr_content},
        },
    )
    cert = created["data"]
    cert_id = cert["id"]
    cert_content = cert["attributes"]["certificateContent"]
    print(f"Certificate created: {cert_id} ({cert_type})")

    # 3. .p12
    cer = os.path.join(args.output_dir, "cert.cer")
    if "BEGIN CERTIFICATE" in cert_content:
        with open(cer, "w") as f:
            f.write(cert_content)
    else:
        blob = base64.b64decode(cert_content)
        b64 = base64.b64encode(blob).decode()
        wrapped = "\n".join(b64[i : i + 64] for i in range(0, len(b64), 64))
        with open(cer, "w") as f:
            f.write("-----BEGIN CERTIFICATE-----\n" + wrapped + "\n-----END CERTIFICATE-----\n")
    p12 = os.path.join(args.output_dir, "cert.p12")
    p12_password = pyrandom.token_urlsafe(18)
    run(
        [
            "openssl", "pkcs12", "-export",
            "-inkey", capath, "-in", cer,
            "-out", p12,
            "-passout", f"pass:{p12_password}",
            "-name", "ZWL iOS Distribution",
            "-keypbe", "PBE-SHA1-3DES",
            "-certpbe", "PBE-SHA1-3DES",
            "-macalg", "sha1",
        ]
    )
    with open(p12, "rb") as f:
        p12_b64 = base64.b64encode(f.read()).decode()
    with open(os.path.join(args.output_dir, "cert.p12.b64"), "w", newline="") as f:
        f.write(p12_b64)
    with open(os.path.join(args.output_dir, "p12_password.txt"), "w", newline="") as f:
        f.write(p12_password)

    # 4. Bundle id (must exist in portal; created if missing, ignored by portal later)
    bundle_id_id = find_or_create_bundle_id(token, args.bundle_id)
    print(f"Bundle ID: {args.bundle_id} -> {bundle_id_id}")

    # 5. Optional device registration
    device_ids = []
    if args.devices:
        parsed = [tuple(pair.split("=", 1)) for pair in args.devices.split(",") if pair]
        device_ids = register_devices(token, parsed)

    # 6. Provisioning profile
    relationships = {
        "bundleId": {"data": {"type": "bundleIds", "id": bundle_id_id}},
        "certificates": {"data": [{"type": "certificates", "id": cert_id}]},
    }
    if device_ids:
        relationships["devices"] = {"data": [{"type": "devices", "id": d} for d in device_ids]}
    profile = api(
        token,
        "POST",
        "/v1/profiles",
        {
            "type": "profiles",
            "attributes": {"name": f"ZWL {args.cert_type}",
                           "profileType": profile_type},
            "relationships": relationships,
        },
    )
    profile_content = profile["data"]["attributes"]["profileContent"]
    profile_b64_path = os.path.join(args.output_dir, "profile.b64")
    with open(profile_b64_path, "w", newline="") as f:
        f.write(profile_content)
    print(f"Provisioning profile created ({profile_type})")

    # 7. Summary
    cert_b64 = open(os.path.join(args.output_dir, "cert.p12.b64")).read().strip()
    prof_b64 = open(profile_b64_path).read().strip()
    setup = f"""# iOS signing material generated at {time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime())}

Bundle ID:                {args.bundle_id}
Certificate:              {cert_id} ({cert_type})
Provisioning profile:     {profile['data']['id']} ({profile_type})

Add these THREE GitHub repo secrets (Settings -> Secrets and variables -> Actions):

1. APPLE_CERTIFICATE  = the base64 string in cert.p12.b64
2. APPLE_CERT_PASSWORD = the password in p12_password.txt
3. APPLE_PROVISIONING_PROFILE = the base64 string in profile.b64

You also need (already known from the portal):
4. APPLE_DEVELOPMENT_TEAM = the 10-char Team ID from developer.apple.com/account

IMPORTANT:
- This material is private (it can sign builds for your bundle ID).
  Store it only in repo secrets; never commit it.
- Run this workflow ONCE. Re-running creates an extra certificate
  (Apple allows max 2 active distribution certs) and a new profile.
- Then run the 'iOS Release (TestFlight)' workflow to build and upload.
"""
    with open(os.path.join(args.output_dir, "SETUP.md"), "w", newline="") as f:
        f.write(setup)
    print(setup)


if __name__ == "__main__":
    main()