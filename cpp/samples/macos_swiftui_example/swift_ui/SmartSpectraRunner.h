#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSInteger, SmartSpectraCameraFacing) {
    SmartSpectraCameraFacingUnknown,
    SmartSpectraCameraFacingFront,
    SmartSpectraCameraFacingBack,
};

typedef NS_ENUM(NSInteger, SmartSpectraCameraLensType) {
    SmartSpectraCameraLensTypeUnknown,
    SmartSpectraCameraLensTypeWideAngle,
    SmartSpectraCameraLensTypeUltraWide,
    SmartSpectraCameraLensTypeTelephoto,
};

@interface SmartSpectraCamera : NSObject
@property(nonatomic, copy, readonly) NSString *cameraID;
@property(nonatomic, copy, readonly, nullable) NSString *name;
@property(nonatomic, readonly) SmartSpectraCameraFacing facing;
@property(nonatomic, readonly) SmartSpectraCameraLensType lensType;
@end

@protocol SmartSpectraRunnerDelegate <NSObject>
- (void)smartSpectraRunnerDidUpdateFrame:(NSImage *)image;
- (void)smartSpectraRunnerDidUpdateStatus:(NSString *)processing validation:(NSString *)validation;
- (void)smartSpectraRunnerDidUpdateMetrics:(NSArray<NSString *> *)metrics timestampUs:(long long)timestampUs;
- (void)smartSpectraRunnerDidUpdateBreathingTrace:(NSArray<NSNumber *> *)breathingTrace
                            arterialPressureTrace:(NSArray<NSNumber *> *)arterialPressureTrace
                                         edaTrace:(NSArray<NSNumber *> *)edaTrace
                                      timestampUs:(long long)timestampUs;
- (void)smartSpectraRunnerDidUpdateDiagnostics:(NSString *)diagnostics;
- (void)smartSpectraRunnerDidFail:(NSString *)message;
@end

@interface SmartSpectraRunner : NSObject
@property(nonatomic, weak, nullable) id<SmartSpectraRunnerDelegate> delegate;

+ (NSString *)sdkVersion;
+ (nullable NSArray<SmartSpectraCamera *> *)availableCamerasWithError:(NSError **)error;
// nil selects Default; a non-nil ID requires that exact discovered camera.
- (nullable NSString *)startWithAPIKey:(NSString *)apiKey cameraID:(nullable NSString *)cameraID;
- (void)stop;
@end

NS_ASSUME_NONNULL_END
