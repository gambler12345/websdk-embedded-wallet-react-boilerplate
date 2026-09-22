from pathlib import Path

base = Path(__file__).resolve().parent
original = (base / "patch_v108.py").read_text(encoding="utf-8")
old = '''def replace_once(old: str, new: str, label: str) -> None:\n    global text\n    count = text.count(old)\n    if count != 1:\n        raise RuntimeError(f"{label}: expected exactly one match, got {count}")\n    text = text.replace(old, new, 1)\n'''
new = '''def replace_once(old: str, new: str, label: str) -> None:\n    global text\n    count = text.count(old)\n    # startLoop and resumeLoop intentionally share this prefix in v1.0.7.\n    # Patch startLoop first; the later, more specific resume replacement then remains unique.\n    if label == "start strict waiting":\n        if count < 1:\n            raise RuntimeError(f"{label}: expected at least one match, got {count}")\n        text = text.replace(old, new, 1)\n        return\n    if count != 1:\n        raise RuntimeError(f"{label}: expected exactly one match, got {count}")\n    text = text.replace(old, new, 1)\n'''
if old not in original:
    raise RuntimeError("Could not patch replace_once helper")
patched = original.replace(old, new, 1)
exec(compile(patched, str(base / "patch_v108.py"), "exec"), {"__file__": str(base / "patch_v108.py"), "__name__": "__main__"})
