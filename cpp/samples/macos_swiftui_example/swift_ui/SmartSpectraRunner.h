#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@protocol SmartSpectraRunnerDelegate <NSObject>
- (void)smartSpectraRunnerDidUpdateFrame:(NSImage *)image;
- (void)smartSpectraRunnerDidUpdateStatus:(NSString *)processing validation:(NSString *)validation;
- (void)smartSpectraRunnerDidUpdateMetrics:(NSArray<NSString *> *)metrics timestampUs:(long long)timestampUs;
- (void)smartSpectraRunnerDidUpdateBreathingTrace:(NSArray<NSNumber *> *)breathingTrace
                            arterialPressureTrace:(NSArray<NSNumber *> *)arterialPressureTrace
                                      timestampUs:(long long)timestampUs;
- (void)smartSpectraRunnerDidUpdateDiagnostics:(NSString *)diagnostics;
- (void)smartSpectraRunnerDidFail:(NSString *)message;
@end

@interface SmartSpectraRunner : NSObject
@property(nonatomic, weak, nullable) id<SmartSpectraRunnerDelegate> delegate;

- (nullable NSString *)startWithAPIKey:(NSString *)apiKey;
- (void)stop;
@end

NS_ASSUME_NONNULL_END
