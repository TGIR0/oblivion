import os

def main():
    os.system('cls' if os.name == 'nt' else 'clear')
    print("====================================================")
    print("      OBLIVION EXTERNAL INTERACTIVE TERMINAL       ")
    print("====================================================")
    print(" TASK COMPLETED. Waiting for next instruction.")
    print("----------------------------------------------------")
    print(" Type your request and press ENTER.")
    print(" Type 'stop' to exit.")
    print("====================================================")
    
    try:
        user_input = input("\nNext Task > ")
        with open("INPUT_SIGNAL.txt", "w", encoding="utf-8") as f:
            f.write(user_input.strip())
    except Exception as e:
        with open("INPUT_SIGNAL.txt", "w", encoding="utf-8") as f:
            f.write(f"ERROR: {e}")

if __name__ == "__main__":
    main()
