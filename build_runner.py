import argparse
import subprocess
import sys


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build script aligned with xiaoailand: run Gradle assemble tasks directly."
    )
    parser.add_argument("-t", "--type", choices=["debug", "release"], help="build type")
    parser.add_argument("--clean", action="store_true", help="run clean before assemble")
    parser.add_argument("--no-daemon", action="store_true", help="pass --no-daemon to Gradle")
    return parser.parse_args()


def choose_build_type_interactive() -> str:
    while True:
        print("Select build type:")
        print("1) debug")
        print("2) release")
        choice = input("Enter 1 or 2: ").strip()
        if choice == "1":
            return "debug"
        if choice == "2":
            return "release"
        print("Invalid choice, try again.\n")


def main() -> int:
    args = parse_args()
    build_type = args.type or choose_build_type_interactive()
    task = "assembleRelease" if build_type == "release" else "assembleDebug"

    cmd = ["cmd", "/c", "gradlew.bat"]
    if args.clean:
        cmd.append("clean")
    cmd.append(task)
    if args.no_daemon:
        cmd.append("--no-daemon")

    print(f"Build type: {build_type}")
    print(f"Run: {' '.join(cmd)}")
    process = subprocess.Popen(cmd)
    process.wait()
    return process.returncode


if __name__ == "__main__":
    sys.exit(main())
