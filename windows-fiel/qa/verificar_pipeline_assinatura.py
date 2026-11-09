"""QA offline for the manual-only Windows signing workflow.

Uses YAML BaseLoader so GitHub's 'on' key is never mistaken for a boolean.
Does not run SignTool, access certificate storage or sign any file.
"""
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / ".github/workflows/brother-windows-authenticode-release.yml"
raw = path.read_text(encoding="utf-8")
workflow = yaml.load(raw, Loader=yaml.BaseLoader)
assert isinstance(workflow, dict), "Workflow YAML must be a mapping"
assert set(workflow["on"]) == {"workflow_dispatch"}, "Never sign on push/PR"
assert workflow["permissions"] == {"contents": "read"}
assert workflow["concurrency"]["cancel-in-progress"] == "false"
jobs = workflow["jobs"]
assert set(jobs) == {"build", "sign", "publish"}
build, sign, publish = jobs["build"], jobs["sign"], jobs["publish"]
assert build["if"] == "github.ref == 'refs/heads/main'", "Only protected main"
assert build["runs-on"].startswith("windows-")
assert sign["needs"] == "build"
assert sign["environment"] == "brother-windows-code-signing"
assert sign["runs-on"] == ["self-hosted", "Windows", "X64", "brother-signing"]
assert sign["permissions"] == {"contents": "read"}
assert set(sign["env"]) == {
    "BM_SIGNING_ENABLED", "BM_CERT_THUMBPRINT",
    "BM_SIGNER_SUBJECT", "BM_RFC3161_TIMESTAMP"
}
assert "secrets." not in raw, "No GitHub secret should contain an OV key"
assert "id-token: write" not in raw
assert not any(
    step.get("uses", "").startswith("actions/checkout")
    for step in sign["steps"]
), "Never run the repository's build scripts on the signing runner"
build_steps = "\n".join(step.get("run", "") for step in build["steps"])
sign_steps = "\n".join(step.get("run", "") for step in sign["steps"])
assert "QA interna" in build_steps and "0.0.1" in build_steps
assert "gradle -p windows-fiel test" in build_steps
assert "createDistributable" in build_steps
assert "Get-AuthenticodeSignature" in build_steps
for marker in (
    "BM_SIGNING_ENABLED -ne 'approved'", "Get-ChildItem Cert:",
    "HasPrivateKey", "EnhancedKeyUsageList", "BM_SIGNER_SUBJECT",
    "'/fd','SHA256'", "'/td','SHA256'",
    "signtool.exe verify /pa /all /tw /v",
    "TimeStamperCertificate", "Get-FileHash",
    "Get-AuthenticodeSignature",
):
    assert marker in sign_steps, f"Missing gate: {marker}"
sign_action = sign["steps"][-1]
assert sign_action["uses"] == "actions/upload-artifact@v4"
assert sign_action["with"]["path"] == "incoming/"
assert sign["steps"][-2]["name"].startswith("Assinar e validar")
assert "deploy" not in sign_action["name"].lower()
assert "publish" not in sign_action["name"].lower()
# Public release runs only after protected Authenticode signing and an
# independently reviewed release environment on the protected main branch.
assert publish["needs"] == "sign"
assert publish["environment"] == "brother-windows-release-publication"
assert publish["permissions"] == {"contents": "write"}
assert "github.ref == 'refs/heads/main'" in publish["if"]
assert "needs.sign.result == 'success'" in publish["if"]
assert publish["runs-on"].startswith("windows-")
assert not any(
    step.get("uses", "").startswith("actions/checkout")
    for step in publish["steps"]
)
release_commands = "\n".join(step.get("run", "") for step in publish["steps"])
for required in (
    "BM_PUBLISHING_ENABLED -ne 'approved'",
    "Get-AuthenticodeSignature", "TimeStamperCertificate",
    "verify /pa /all /tw /v", "SHA256SUMS.txt", "Compress-Archive",
    "z.testzip() is None", "gh release create", "--latest=false",
):
    assert required in release_commands, f"Missing release safety gate: {required}"
assert publish["steps"][-1]["env"]["GH_TOKEN"] == "$" + "{{ github.token }}"
assert "secrets." not in raw
print("BROTHER_WINDOWS_AUTHENTICODE_MANUAL_OV_HSM_POLICY_QA_OK")
