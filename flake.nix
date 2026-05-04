{
  description = "oblivion";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-unstable";
    devshell.url = "github:numtide/devshell";
    flake-utils.url = "github:numtide/flake-utils";
    android.url = "github:tadfisher/android-nixpkgs";
  };

  outputs = { self, nixpkgs, devshell, flake-utils, android }:
    {
      overlay = final: prev: {
        inherit (self.packages.${final.system}) android-sdk android-studio;
      };
    }
    //
    flake-utils.lib.eachSystem [ "aarch64-darwin" "x86_64-darwin" "x86_64-linux" ] (system:
      let
        inherit (nixpkgs) lib;
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          overlays = [
            devshell.overlays.default
            self.overlay
          ];
        };
      in
      {
        packages = {
          android-sdk = android.sdk.${system} (sdkPkgs: with sdkPkgs; [
            # Core build tools and platform definitions
            build-tools-36-0-0
            cmdline-tools-latest
            emulator
            platform-tools
            platforms-android-36

            # NDK matching the project's build.gradle requirement
            ndk-29-0-14206865
          ]
          ++ lib.optionals (system == "x86_64-darwin" || system == "x86_64-linux") [
            system-images-android-36-google-apis-x86-64
            system-images-android-36-google-apis-playstore-x86-64
          ]);
        } // lib.optionalAttrs (system == "x86_64-linux") {
          # Android Studio – the latest stable release for Linux
          android-studio = pkgs.androidStudioPackages.stable;
          # Uncomment one of the following for alternate channels:
          # android-studio = pkgs.androidStudioPackages.beta;
          # android-studio = pkgs.androidStudioPackages.preview;
          # android-studio = pkgs.androidStudioPackage.canary;
        };

        devShell = import ./devshell.nix { inherit pkgs; };
      }
    );
}