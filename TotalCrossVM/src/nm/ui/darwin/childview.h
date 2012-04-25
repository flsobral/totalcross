/*********************************************************************************
 *  TotalCross Software Development Kit                                          *
 *  Copyright (C) 2000-2012 SuperWaba Ltda.                                      *
 *  All Rights Reserved                                                          *
 *                                                                               *
 *  This library and virtual machine is distributed in the hope that it will     *
 *  be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of    *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.                         *
 *                                                                               *
 *********************************************************************************/

#import <Foundation/Foundation.h>
#import <CoreFoundation/CoreFoundation.h>
#import <UIKit/UIKit.h>
#import <UIKit/UITextView.h>
#import <QuartzCore/CALayer.h>

@interface ChildView : UIView
{
   int orientation;
   CGContextRef bitmapContext;
   CGImageRef cgImage;
   int width, height, pitch;
   int lastEventTS;
   int shiftY;
}
- (id)initWithFrame:(CGRect)rect orientation:(int)orient;
- (void)updateScreen:(void*)screen;
- (void)drawRect:(CGRect)frame;
- (void)invalidateScreen:(void*)vscreen;

- (void)touchesBegan:(NSSet *)touches withEvent:(UIEvent *)event;
- (void)touchesMoved:(NSSet *)touches withEvent:(UIEvent *)event;
- (void)touchesEnded:(NSSet *)touches withEvent:(UIEvent *)event;
- (void)touchesCancelled:(NSSet *)touches withEvent:(UIEvent *)event;
- (void)screenChange:(int)w height:(int)h;
- (void)addEvent:(NSDictionary*)event;

@end
