import SwiftUI
import shared

/// Full-screen out-of-zone status with live compass, mirroring Android `OutsideZoneContent.kt`.
struct OutsideZoneView: View {
    let nearestDistrict: String
    let distanceMeters: Double
    let bearingDegrees: Float
    let azimuth: Float
    let ban: ForestBan?
    let onBanTap: () -> Void
    let onDistrictTap: () -> Void
    var onDebugToggle: (() -> Void)?

    private let compassSize: CGFloat = 220

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if let ban {
                    ForestBanAlertBanner(forestBan: ban, onTap: onBanTap)
                        .padding(.bottom, 16)
                }

                VStack(spacing: 24) {
                    ZStack {
                        Circle()
                            .fill(ZWL.amberAccent.opacity(0.1))
                            .frame(width: 100, height: 100)
                            .overlay(Circle().stroke(ZWL.yellowPrimary, lineWidth: 3))
                        Text("!")
                            .font(.system(size: 48, weight: .black))
                            .foregroundColor(ZWL.yellowPrimary)
                    }
                    .onTapGesture {
                        onDebugToggle?()
                    }

                    Text("Jesteś poza strefą\nprogramu \"Zanocuj w Lesie\"")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineSpacing(6)
                }
                .padding(.top, 24)

                CompassView(azimuth: azimuth, bearing: bearingDegrees, size: compassSize)
                    .padding(.vertical, 16)

                nearestZoneCard
                    .padding(.bottom, 24)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .background(ZWL.yellowBackground.ignoresSafeArea())
    }

    private var nearestZoneCard: some View {
        Button(action: onDistrictTap) {
            VStack(spacing: 4) {
                Text("NAJBLIŻSZA STREFA")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)

                HStack(spacing: 8) {
                    Text(nearestDistrict)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 18))
                        .foregroundColor(ZWL.yellowPrimary)
                }

                Text(Formatters.nearestDistanceText(distanceMeters))
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(ZWL.riskLow)
                    .multilineTextAlignment(.center)

                Text("Kierunek: \(Formatters.cardinalDirectionText(bearingDegrees))")
                    .font(.system(size: 14))
                    .foregroundColor(ZWL.lightGray)
                    .multilineTextAlignment(.center)
            }
            .padding(20)
            .frame(maxWidth: .infinity)
            .background(ZWL.yellowSurface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

/// Live compass: the whole card (border + N/S/W/E labels + needle) rotates by
/// -azimuth so the top always faces north; the needle additionally rotates by the
/// bearing toward the nearest zone. Mirrors Android's `OutsideZoneContent` layout.
struct CompassView: View {
    let azimuth: Float
    let bearing: Float
    let size: CGFloat

    @State private var animatedAzimuth: Float = 0

    private var innerPadding: CGFloat { max(8, size * 0.06) }
    private var needleSize: CGFloat { size * 0.36 }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size / 2)
                .stroke(ZWL.darkGray, lineWidth: 2)
                .frame(width: size, height: size)

            ZStack {
                Text("N")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .position(x: size / 2, y: innerPadding + 8)
                Text("S")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .position(x: size / 2, y: size - innerPadding - 8)
                Text("W")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .position(x: innerPadding + 8, y: size / 2)
                Text("E")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .position(x: size - innerPadding - 8, y: size / 2)

                CompassNeedle()
                    .fill(ZWL.yellowPrimary)
                    .frame(width: needleSize, height: needleSize)
                    .rotationEffect(.degrees(Double(bearing)))
            }
            .frame(width: size, height: size)
            .rotationEffect(.degrees(Double(-animatedAzimuth)))
        }
        .frame(width: size, height: size)
        .onChange(of: azimuth) { newAzimuth in
            let diff = (newAzimuth - animatedAzimuth).truncatingRemainder(dividingBy: 360)
            let shortest = diff > 180 ? diff - 360 : (diff < -180 ? diff + 360 : diff)
            withAnimation(.easeOut(duration: 0.2)) {
                animatedAzimuth += shortest
            }
        }
        .onAppear {
            animatedAzimuth = azimuth
        }
    }
}

/// Triangle needle, tip at top center (mirrors Android `Canvas` path in OutsideZoneContent.kt).
struct CompassNeedle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY * 0.75))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}