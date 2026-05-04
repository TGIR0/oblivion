{ pkgs }:

with pkgs;

# Configure your development environment.
#
# Documentation: https://github.com/numtide/devshell
devshell.mkShell {
  name = "oblivion";
  motd = ''
    Entered the Android 16 (Baklava) app development environment.
  '';
  env = [
    {
      name = "ANDROID_HOME";
      value = "${android-sdk}/share/android-sdk";
    }
    {
      name = "ANDROID_SDK_ROOT";
      value = "${android-sdk}/share/android-sdk";
    }
    {
      name = "JAVA_HOME";
      value = jdk25.home;
    }
    {
      name = "GOBIN";
      eval = "$HOME/go/bin";
    }
    {
      name = "PATH";
      eval = "$HOME/go/bin:$PATH";
    }
  ];
  packages = [
    android-studio
    android-sdk
    gradle
    jdk25
    go_1_25
    cmake
    ninja
  ];
}
