import SwiftUI
import shared

/// Pending (unsaved) point card — opened by a long-press on the map or a
/// `zwl://point` deep link. Shows the zone status + forest ban computed
/// offline by the shared spatial engine and offers Save (with a name prompt),
/// Share (system share sheet) and Close (Android `PointDetailCard` parity).
struct PointDetailView: View {
    let point: PendingPoint
    let onSave: (String) -> Void
    let shareLink: String
    let onClose: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var isSavePromptPresented = false

    init(point: PendingPoint,
         onSave: @escaping (String) -> Void,
         shareLink: String,
         onClose: @escaping () -> Void) {
        self.point = point
        self.onSave = onSave
        self.shareLink = shareLink
        self.onClose = onClose
        _name = State(initialValue: point.name)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            headerRow

            coordinatesSubcard

            if point.status != nil {
                zoneSubcard
            }

            if point.ban != nil {
                banSubcard
            }

            Spacer(minLength: 4)

            HStack(spacing: 12) {
                Button(action: { isSavePromptPresented = true }) {
                    Label("Zapisz", systemImage: "bookmark")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)

                ShareLink(item: shareLink) {
                    Label("Podziel się", systemImage: "square.and.arrow.up")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button(action: {
                    onClose()
                    dismiss()
                }) {
                    Label("Zamknij", systemImage: "xmark")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
        .alert("Nazwa punktu", isPresented: $isSavePromptPresented) {
            TextField("Zapisany punkt", text: $name)
            Button("Zapisz") { onSave(name) }
            Button("Anuluj", role: .cancel) {}
        } message: {
            Text("Wpisz nazwę, pod którą chcesz zapisać ten punkt.")
        }
    }

    private var headerRow: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Wybrany punkt")
                    .font(.system(size: 18, weight: .bold))
                Text("Długi tap albo wysłany link")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
            Spacer()
            Button(action: {
                onClose()
                dismiss()
            }) {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.secondary)
                    .frame(width: 28, height: 28)
                    .background(Color(.secondarySystemBackground), in: Circle())
            }
        }
    }

    private var coordinatesSubcard: some View {
        subcard {
            Text("WSPÓŁRZĘDNE")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.secondary)
            Text(Formatters.coordinateText(latitude: point.latitude, longitude: point.longitude))
                .font(.system(size: 13))
                .padding(.top, 4)
        }
    }

    private var zoneSubcard: some View {
        subcard {
            Text("STREFA")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.secondary)
            Text(zoneStatusText)
                .font(.system(size: 13))
                .padding(.top, 4)
        }
    }

    private var banSubcard: some View {
        subcard {
            Text("ZAKAZ WSTĘPU")
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(ZWL.errorRedAccent)
            Text(banText)
                .font(.system(size: 13))
                .foregroundColor(ZWL.errorRedText)
                .padding(.top, 4)
        }
    }

    private func subcard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var zoneStatusText: String {
        guard let status = point.status else { return "Obliczanie..." }
        if let inZone = status as? LocationStatusInZone {
            return "W strefie: \(inZone.forestDistrict)"
        }
        if let outside = status as? LocationStatusOutsideZone {
            let distance = Formatters.approximateDistanceText(outside.distanceMeters)
            return "Poza strefą (najbliżej: \(outside.nearestDistrict), ok. \(distance))"
        }
        return "Brak danych o strefach"
    }

    private var banText: String {
        guard let ban = point.ban else { return "Brak zakazu" }
        if let desc = ban.banDescription, !desc.isEmpty { return desc }
        return ban.forestDistrictName
    }
}