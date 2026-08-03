package com.smartmed.backend.records.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartmed.backend.consultation.entity.Consultation;
import com.smartmed.backend.order.entity.DrugOrder;
import com.smartmed.backend.order.entity.MedicationReminder;
import com.smartmed.backend.prescription.entity.Prescription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * C 端记录查询 Mapper（08 ticket）。
 * <p>
 * 全部按"实际就诊人"过滤（ADR-0010 口径）：
 * <ul>
 *   <li>familyMemberId 为 null → 本人就诊（registration.family_member_id IS NULL）</li>
 *   <li>familyMemberId 非 null → 该成员就诊（registration.family_member_id = ?）</li>
 * </ul>
 * 问诊/处方/订单/提醒均经 consultation → registration 关联（表无冗余成员列）。
 */
@Mapper
public interface RecordsMapper {

    /** C 端问诊分页（JOIN registration 按成员口径过滤）。 */
    @Select("""
            <script>
            SELECT c.* FROM consultation c
            JOIN registration r ON c.registration_id = r.id
            WHERE r.patient_id = #{patientId}
            <choose>
              <when test="familyMemberId != null">AND r.family_member_id = #{familyMemberId}</when>
              <otherwise>AND r.family_member_id IS NULL</otherwise>
            </choose>
            ORDER BY c.created_at DESC
            </script>
            """)
    IPage<Consultation> pageConsultations(Page<Consultation> page,
                                          @Param("patientId") Long patientId,
                                          @Param("familyMemberId") Long familyMemberId);

    /** 单条问诊归属校验（C 端：操作人 = 当前患者）。 */
    @Select("""
            SELECT c.* FROM consultation c
            JOIN registration r ON c.registration_id = r.id
            WHERE c.id = #{consultationId} AND r.patient_id = #{patientId}
            """)
    Consultation findConsultationByPatient(@Param("consultationId") Long consultationId,
                                           @Param("patientId") Long patientId);

    /** C 端处方分页（JOIN consultation JOIN registration 按成员口径过滤）。 */
    @Select("""
            <script>
            SELECT p.* FROM prescription p
            JOIN consultation c ON p.consultation_id = c.id
            JOIN registration r ON c.registration_id = r.id
            WHERE r.patient_id = #{patientId}
            <choose>
              <when test="familyMemberId != null">AND r.family_member_id = #{familyMemberId}</when>
              <otherwise>AND r.family_member_id IS NULL</otherwise>
            </choose>
            ORDER BY p.created_at DESC
            </script>
            """)
    IPage<Prescription> pagePrescriptions(Page<Prescription> page,
                                           @Param("patientId") Long patientId,
                                           @Param("familyMemberId") Long familyMemberId);

    /** 单条处方归属校验（C 端：操作人 = 当前患者）。 */
    @Select("""
            SELECT p.* FROM prescription p
            JOIN consultation c ON p.consultation_id = c.id
            JOIN registration r ON c.registration_id = r.id
            WHERE p.id = #{prescriptionId} AND r.patient_id = #{patientId}
            """)
    Prescription findPrescriptionByPatient(@Param("prescriptionId") Long prescriptionId,
                                           @Param("patientId") Long patientId);

    /** C 端购药订单分页（JOIN prescription → consultation → registration）。 */
    @Select("""
            <script>
            SELECT o.* FROM drug_order o
            JOIN prescription p ON o.prescription_id = p.id
            JOIN consultation c ON p.consultation_id = c.id
            JOIN registration r ON c.registration_id = r.id
            WHERE r.patient_id = #{patientId}
            <choose>
              <when test="familyMemberId != null">AND r.family_member_id = #{familyMemberId}</when>
              <otherwise>AND r.family_member_id IS NULL</otherwise>
            </choose>
            ORDER BY o.created_at DESC
            </script>
            """)
    IPage<DrugOrder> pageOrders(Page<DrugOrder> page,
                                @Param("patientId") Long patientId,
                                @Param("familyMemberId") Long familyMemberId);

    /** 单条订单归属校验（C 端：操作人 = 当前患者）。 */
    @Select("""
            SELECT o.* FROM drug_order o
            JOIN prescription p ON o.prescription_id = p.id
            JOIN consultation c ON p.consultation_id = c.id
            JOIN registration r ON c.registration_id = r.id
            WHERE o.id = #{orderId} AND r.patient_id = #{patientId}
            """)
    DrugOrder findOrderByPatient(@Param("orderId") Long orderId,
                                 @Param("patientId") Long patientId);

    /** C 端用药提醒列表（按成员口径过滤，下次提醒时间升序）。 */
    @Select("""
            <script>
            SELECT m.* FROM medication_reminder m
            JOIN prescription p ON m.prescription_id = p.id
            JOIN consultation c ON p.consultation_id = c.id
            JOIN registration r ON c.registration_id = r.id
            WHERE r.patient_id = #{patientId}
            <choose>
              <when test="familyMemberId != null">AND r.family_member_id = #{familyMemberId}</when>
              <otherwise>AND r.family_member_id IS NULL</otherwise>
            </choose>
            ORDER BY m.next_remind_at ASC
            </script>
            """)
    List<MedicationReminder> listReminders(@Param("patientId") Long patientId,
                                           @Param("familyMemberId") Long familyMemberId);
}
