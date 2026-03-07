import subprocess
import sys

def run_build():
    try:
        # Run gradlew compileDebugKotlin and capture everything
        process = subprocess.Popen(
            ['cmd', '/c', 'gradlew.bat :app:compileDebugKotlin --no-daemon'],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding='utf-8',
            errors='ignore'
        )
        
        with open('build_output_full.txt', 'w', encoding='utf-8') as f:
            for line in process.stdout:
                sys.stdout.write(line)
                f.write(line)
        
        process.wait()
        print(f"\nBuild finished with exit code: {process.returncode}")
    except Exception as e:
        print(f"Error running build: {e}")

if __name__ == "__main__":
    run_build()
