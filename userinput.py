import os
import time
import subprocess

def get_external_input():
    # Remove old signal if exists
    if os.path.exists("INPUT_SIGNAL.txt"):
        os.remove("INPUT_SIGNAL.txt")
    
    # Launch external terminal precisely as user suggested
    # This will open a NEW window and wait for it to close
    print("[SYSTEM] Launching External Terminal...")
    cmd = 'cmd /c "start /wait python userinput_ext.py"'
    subprocess.call(cmd, shell=True)
    
    # After window closes, read the signal
    if os.path.exists("INPUT_SIGNAL.txt"):
        with open("INPUT_SIGNAL.txt", "r", encoding="utf-8") as f:
            return f.read().strip()
    return "stop" # Default to stop if no signal found

if __name__ == "__main__":
    result = get_external_input()
    print(result)