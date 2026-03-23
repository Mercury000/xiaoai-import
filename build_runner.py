import argparse
import subprocess
import sys


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build script aligned with xiaoailand: run Gradle assemble tasks directly."
    )
    parser.add_argument("--type", choices=["debug", "release"], default="debug", help="build type")
    parser.add_argument("--clean", action="store_true", help="run clean before assemble")
    parser.add_argument("--no-daemon", action="store_true", help="pass --no-daemon to Gradle")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    task = "assembleRelease" if args.type == "release" else "assembleDebug"

    cmd = ["cmd", "/c", "gradlew.bat"]
    if args.clean:
        cmd.append("clean")
    cmd.append(task)
    if args.no_daemon:
        cmd.append("--no-daemon")

    print(f"Run: {' '.join(cmd)}")
    process = subprocess.Popen(cmd)
    process.wait()
    return process.returncode


if __name__ == "__main__":
    sys.exit(main())
